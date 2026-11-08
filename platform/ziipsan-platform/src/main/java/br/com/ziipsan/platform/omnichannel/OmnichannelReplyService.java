package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.core.sac.AssignmentChange;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.multichannel.ChannelPricing;
import br.com.ziipsan.platform.notification.multichannel.MultichannelParameters;
import br.com.ziipsan.platform.notification.multichannel.TemplateCategoryRule;
import br.com.ziipsan.platform.notification.multichannel.UsageMetric;
import br.com.ziipsan.platform.notification.multichannel.UsageRecord;
import br.com.ziipsan.platform.notification.multichannel.UsageStore;
import br.com.ziipsan.platform.notification.multichannel.WhatsAppCategory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A tela do Ziip SAN respondendo (ED-42): o atendente responde na
 * conversa, a resposta sai pelo canal DELA e fica registrada na
 * conversa/protocolo. Guardas, nesta ordem:
 *
 * <ol>
 *   <li>conversa existe e está aberta (encerrada não responde);</li>
 *   <li>canal habilitado no tenant e provedor aprovado no DPA;</li>
 *   <li>opt-in do morador no canal (canais externos): revogado ou
 *       ausente NÃO recebe resposta ativa — a conversa continua
 *       registrada e o atendimento segue por outro canal/protocolo; o
 *       chat do portal é sessão iniciada pelo próprio morador e não
 *       passa por esta guarda (declarado em
 *       {@link OmnichannelChannel#requiresOptInForReply()});</li>
 *   <li>resposta de WhatsApp POR MODELO: o modelo precisa estar
 *       classificado em {@code msg.template_category_map} da ED-41 e ter
 *       preço vigente na porta de preços dela — sem classificação ou sem
 *       preço, a resposta por modelo não sai (texto livre na janela do
 *       morador não usa modelo: {@code templateCode == null});</li>
 *   <li>envio pela porta do canal, mensagem gravada, conversa aguarda o
 *       morador, integridade lançada e uso medido na ED-41 com o custo
 *       da categoria do modelo (GES-19: custo por categoria de
 *       template lido da mensageria da ED-41, não recalculado aqui —
 *       a estimativa é a {@link TemplateCategoryRule#estimate} dela).</li>
 * </ol>
 *
 * <p><b>Transferência para humano / redistribuição:</b>
 * {@link #transfer} consome a trilha da ED-35
 * ({@link AssignmentChange#redistribute} sobre o PROTOCOLO da
 * conversa, motivo obrigatório) — é o mesmo mecanismo pelo qual a
 * deflexão da P4.8 (corrigida no M3) entrega a conversa ao humano:
 * quem transfere é esta tela; a regra de QUANDO transferir (pedido do
 * usuário/sem artigo) é da P4.8 e não é duplicada aqui.
 */
public class OmnichannelReplyService {

    public enum Outcome {
        SENT, CONVERSATION_NOT_FOUND, CONVERSATION_CLOSED, CHANNEL_DISABLED,
        PROVIDER_NOT_IN_DPA, OPT_IN_MISSING, OPT_IN_REVOKED,
        TEMPLATE_NOT_CLASSIFIED, PRICE_NOT_CONFIGURED
    }

    public record ReplyResult(Outcome outcome, UUID messageId, BigDecimal cost,
                              WhatsAppCategory category, String reason) {
        public boolean sent() { return outcome == Outcome.SENT; }
    }

    private final OmnichannelParameters parameters;
    private final OmnichannelPorts.OmniProviderRegistry providerRegistry;
    private final OmnichannelPorts.ConversationStore store;
    private final OmnichannelPorts.ConversationGateway gateway;
    private final OmnichannelPorts.OptInPort optInPort;
    private final OmnichannelPorts.IntegrityLogStore integrityLog;
    private final MultichannelParameters multichannelParameters;
    private final ChannelPricing pricing;
    private final UsageStore usageStore;
    private final Clock clock;

    public OmnichannelReplyService(OmnichannelParameters parameters,
                                   OmnichannelPorts.OmniProviderRegistry providerRegistry,
                                   OmnichannelPorts.ConversationStore store,
                                   OmnichannelPorts.ConversationGateway gateway,
                                   OmnichannelPorts.OptInPort optInPort,
                                   OmnichannelPorts.IntegrityLogStore integrityLog,
                                   MultichannelParameters multichannelParameters,
                                   ChannelPricing pricing, UsageStore usageStore, Clock clock) {
        this.parameters = Objects.requireNonNull(parameters, "parâmetros omni.* obrigatórios");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "registro DPA obrigatório");
        this.store = Objects.requireNonNull(store, "persistência de conversas obrigatória");
        this.gateway = Objects.requireNonNull(gateway, "gateway do canal obrigatório");
        this.optInPort = Objects.requireNonNull(optInPort, "porta de opt-in obrigatória");
        this.integrityLog = Objects.requireNonNull(integrityLog, "log de integridade obrigatório");
        this.multichannelParameters = Objects.requireNonNull(multichannelParameters,
                "parâmetros da mensageria da ED-41 obrigatórios");
        this.pricing = Objects.requireNonNull(pricing, "preços da ED-41 obrigatórios");
        this.usageStore = Objects.requireNonNull(usageStore, "medição da ED-41 obrigatória");
        this.clock = Objects.requireNonNull(clock, "relógio obrigatório");
    }

    public ReplyResult reply(UUID tenantId, UUID conversationId, UUID attendantId,
                             String body, String templateCode) {
        Objects.requireNonNull(tenantId, "tenant obrigatório");
        Objects.requireNonNull(conversationId, "conversa obrigatória");
        Objects.requireNonNull(attendantId, "atendente obrigatório");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("a resposta exige o texto do atendente");
        }
        Optional<Conversation> found = store.findById(tenantId, conversationId);
        if (found.isEmpty()) {
            return refuse(tenantId, conversationId, null, attendantId, Outcome.CONVERSATION_NOT_FOUND,
                    "conversa inexistente");
        }
        Conversation conversation = found.get();
        if (!conversation.isOpen()) {
            return refuse(tenantId, conversationId, conversation.channel(), attendantId,
                    Outcome.CONVERSATION_CLOSED, "conversa encerrada");
        }
        OmnichannelChannel channel = conversation.channel();
        if (!parameters.channelsEnabled(tenantId).contains(channel)) {
            return refuse(tenantId, conversationId, channel, attendantId, Outcome.CHANNEL_DISABLED,
                    "canal " + channel + " fora de omni.channels_enabled do tenant");
        }
        if (!providerRegistry.providerApproved(tenantId, channel)) {
            return refuse(tenantId, conversationId, channel, attendantId, Outcome.PROVIDER_NOT_IN_DPA,
                    "sem provedor aprovado no DPA para o canal " + channel);
        }
        if (channel.requiresOptInForReply()) {
            OmniOptInRule.OptInDecision decision = OmniOptInRule.decide(
                    optInPort.optInRows(tenantId, conversation.customerId(), channel));
            appendIntegrity(tenantId, IntegrityLog.EventType.OPT_IN_CHECKED,
                    conversationId + ":" + channel, attendantId.toString(), clock.instant());
            if (decision == OmniOptInRule.OptInDecision.OPT_IN_MISSING) {
                return new ReplyResult(Outcome.OPT_IN_MISSING, null, null, null,
                        "morador sem opt-in registrado no canal " + channel);
            }
            if (decision == OmniOptInRule.OptInDecision.OPT_IN_REVOKED) {
                return new ReplyResult(Outcome.OPT_IN_REVOKED, null, null, null,
                        "opt-in do morador revogado no canal " + channel);
            }
        }
        WhatsAppCategory category = null;
        BigDecimal cost = null;
        if (channel == OmnichannelChannel.WHATSAPP && templateCode != null) {
            Optional<WhatsAppCategory> classified = TemplateCategoryRule.classify(
                    multichannelParameters.templateCategoryMap(tenantId), templateCode);
            if (classified.isEmpty()) {
                return new ReplyResult(Outcome.TEMPLATE_NOT_CLASSIFIED, null, null, null,
                        "modelo " + templateCode + " fora de msg.template_category_map (ED-41)");
            }
            category = classified.get();
            Optional<BigDecimal> unitPrice = pricing.unitPrice(tenantId, NotificationChannel.WHATSAPP, category);
            if (unitPrice.isEmpty()) {
                return new ReplyResult(Outcome.PRICE_NOT_CONFIGURED, null, null, category,
                        "sem preço vigente do provedor para a categoria " + category + " (ED-41)");
            }
            cost = TemplateCategoryRule.estimate(unitPrice.get(), 1);
        }
        String providerMessageId = gateway.send(tenantId, channel, conversation.externalThreadId(), body);
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalStateException("gateway do canal " + channel + " não devolveu o id da mensagem");
        }
        Instant now = clock.instant();
        ConversationMessage message = new ConversationMessage(UUID.randomUUID(), tenantId, conversationId,
                ConversationMessage.Direction.OUTBOUND, ConversationMessage.Author.ATTENDANT, body,
                providerMessageId, templateCode, category, now);
        store.appendMessage(message);
        store.update(conversation.markReplied());
        appendIntegrity(tenantId, IntegrityLog.EventType.MESSAGE_RECORDED,
                message.id().toString(), attendantId.toString(), now);
        usageStore.append(new UsageRecord(tenantId, OmnichannelInboundService.MODULE_CODE,
                UsageMetric.MESSAGES, 1, cost, message.id(), now));
        return new ReplyResult(Outcome.SENT, message.id(), cost, category,
                "resposta registrada no protocolo " + conversation.protocolId());
    }

    /**
     * Redistribui a conversa para outro atendente/humano com motivo
     * obrigatório, pela trilha da ED-35 sobre o protocolo da conversa.
     */
    public AssignmentChange.TrailEntry transfer(UUID tenantId, UUID conversationId, UUID fromAttendantId,
                                                UUID toAttendantId, String reason, UUID changedBy) {
        Conversation conversation = store.findById(tenantId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("conversa inexistente: " + conversationId));
        if (!conversation.isOpen()) {
            throw new IllegalStateException("conversa encerrada não é redistribuída");
        }
        Instant now = clock.instant();
        AssignmentChange.TrailEntry trail = AssignmentChange.redistribute(
                AssignmentChange.TargetType.PROTOCOL, conversation.protocolId(), fromAttendantId,
                toAttendantId, reason, changedBy, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        store.update(conversation.assign(toAttendantId));
        appendIntegrity(tenantId, IntegrityLog.EventType.ASSIGNMENT_CHANGED,
                conversationId.toString(), changedBy.toString(), now);
        return trail;
    }

    /**
     * Recusa precoce da resposta (conversa inexistente/encerrada, canal
     * desabilitado, provedor fora do DPA): entra na cadeia de integridade
     * como {@code REFUSAL_RECORDED}, referência conversa[:canal]:motivo —
     * nenhuma recusa fica sem rastro verificável.
     */
    private ReplyResult refuse(UUID tenantId, UUID conversationId, OmnichannelChannel channel,
                               UUID attendantId, Outcome outcome, String reason) {
        String reference = conversationId + (channel == null ? "" : ":" + channel) + ":" + outcome;
        appendIntegrity(tenantId, IntegrityLog.EventType.REFUSAL_RECORDED, reference,
                attendantId.toString(), clock.instant());
        return new ReplyResult(outcome, null, null, null, reason);
    }

    private void appendIntegrity(UUID tenantId, IntegrityLog.EventType type, String reference,
                                 String actor, Instant at) {
        integrityLog.append(IntegrityLog.append(integrityLog.nextSequence(tenantId), tenantId, type,
                reference, actor, at, integrityLog.currentHash(tenantId)));
    }
}
