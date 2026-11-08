package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.platform.notification.multichannel.UsageMetric;
import br.com.ziipsan.platform.notification.multichannel.UsageRecord;
import br.com.ziipsan.platform.notification.multichannel.UsageStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Recepção de mensagens do morador (ED-42): o webhook de cada canal é
 * conferido ANTES de qualquer efeito, e a mensagem só vira conversa
 * registrada no protocolo da matrícula depois de passar, nesta ordem:
 *
 * <ol>
 *   <li>canal habilitado no tenant ({@code omni.channels_enabled});</li>
 *   <li>provedor do canal aprovado no DPA do tenant;</li>
 *   <li>webhook conferido pelo modo do canal ({@link WebhookSecurity}):
 *       assinatura HMAC (Meta/e-mail), segredo (Telegram) ou sessão
 *       autenticada (chat do portal). Falha = {@code WEBHOOK_REJECTED},
 *       com lançamento de integridade da REJEIÇÃO — tentativa de webhook
 *       falso também é fato auditável — e nenhum outro efeito;</li>
 *   <li>idempotência: evento do provedor já processado é ignorado
 *       ({@code DUPLICATE_IGNORED});</li>
 *   <li>identidade resolvida pela borda: sem protocolo/matrícula/
 *       morador resolvidos, recusa nomeada — o núcleo não abre conversa
 *       órfã nem inventa protocolo;</li>
 *   <li>conversa aberta pela thread (ou a aberta é reusada), mensagem
 *       gravada, evento marcado, integridade lançada
 *       ({@code WEBHOOK_VERIFIED}, {@code CONVERSATION_OPENED} quando
 *       nova, {@code MESSAGE_RECORDED}) e uso medido na mensageria da
 *       ED-41 ({@link UsageStore}: {@code MESSAGES}, módulo
 *       {@code ED-42}, sem custo — inbound não tem custo de template).</li>
 * </ol>
 *
 * Recusas precoces (canal desabilitado, provedor fora do DPA e segredo
 * de webhook ausente no cofre) também lançam na cadeia de integridade
 * ({@code REFUSAL_RECORDED}, referência canal:evento:motivo, nunca o
 * segredo) — nenhuma recusa fica sem rastro verificável.
 */
public class OmnichannelInboundService {

    public static final String MODULE_CODE = "ED-42";

    public enum Outcome {
        ACCEPTED, CHANNEL_DISABLED, PROVIDER_NOT_IN_DPA, WEBHOOK_REJECTED,
        WEBHOOK_SECRET_MISSING, DUPLICATE_IGNORED, IDENTITY_UNRESOLVED
    }

    public record InboundResult(Outcome outcome, UUID conversationId, String reason) {
        public boolean accepted() { return outcome == Outcome.ACCEPTED; }
    }

    private final OmnichannelParameters parameters;
    private final OmnichannelPorts.OmniProviderRegistry providerRegistry;
    private final OmnichannelPorts.CredentialVaultPort vault;
    private final OmnichannelPorts.ConversationStore store;
    private final OmnichannelPorts.WebhookEventStore webhookEvents;
    private final OmnichannelPorts.IntegrityLogStore integrityLog;
    private final UsageStore usageStore;
    private final Clock clock;

    public OmnichannelInboundService(OmnichannelParameters parameters,
                                     OmnichannelPorts.OmniProviderRegistry providerRegistry,
                                     OmnichannelPorts.CredentialVaultPort vault,
                                     OmnichannelPorts.ConversationStore store,
                                     OmnichannelPorts.WebhookEventStore webhookEvents,
                                     OmnichannelPorts.IntegrityLogStore integrityLog,
                                     UsageStore usageStore, Clock clock) {
        this.parameters = Objects.requireNonNull(parameters, "parâmetros omni.* obrigatórios");
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "registro DPA obrigatório");
        this.vault = Objects.requireNonNull(vault, "cofre obrigatório");
        this.store = Objects.requireNonNull(store, "persistência de conversas obrigatória");
        this.webhookEvents = Objects.requireNonNull(webhookEvents, "idempotência de webhook obrigatória");
        this.integrityLog = Objects.requireNonNull(integrityLog, "log de integridade obrigatório");
        this.usageStore = Objects.requireNonNull(usageStore, "medição da ED-41 obrigatória");
        this.clock = Objects.requireNonNull(clock, "relógio obrigatório");
    }

    /**
     * @param rawPayload      carga bruta do webhook, EXATAMENTE como chegou (a assinatura é sobre ela)
     * @param signatureHeader cabeçalho de assinatura (Meta/provedor de e-mail); ignorado nos demais modos
     * @param secretHeader    cabeçalho de segredo (Telegram); ignorado nos demais modos
     * @param envelope        mensagem traduzida e identidade resolvida pela borda do canal
     */
    public InboundResult accept(UUID tenantId, OmnichannelChannel channel, byte[] rawPayload,
                                String signatureHeader, String secretHeader,
                                OmnichannelPorts.InboundEnvelope envelope) {
        Objects.requireNonNull(tenantId, "tenant obrigatório");
        Objects.requireNonNull(channel, "canal obrigatório");
        Objects.requireNonNull(envelope, "envelope obrigatório");
        Instant now = clock.instant();

        if (!parameters.channelsEnabled(tenantId).contains(channel)) {
            return refuse(tenantId, channel, envelope, now, Outcome.CHANNEL_DISABLED,
                    "canal " + channel + " fora de omni.channels_enabled do tenant");
        }
        if (!providerRegistry.providerApproved(tenantId, channel)) {
            return refuse(tenantId, channel, envelope, now, Outcome.PROVIDER_NOT_IN_DPA,
                    "sem provedor aprovado no DPA para o canal " + channel);
        }
        // (3) conferência do webhook — fail-closed, antes de qualquer efeito.
        switch (channel.webhookMode()) {
            case AUTHENTICATED_SESSION -> {
                if (!envelope.authenticatedSession()) {
                    return reject(tenantId, channel, envelope, now,
                            "chat do portal sem sessão autenticada");
                }
            }
            case META_HMAC_SHA256, PROVIDER_HMAC_SHA256 -> {
                Optional<String> secret = vault.webhookSecret(tenantId, channel);
                if (secret.isEmpty()) {
                    return refuse(tenantId, channel, envelope, now, Outcome.WEBHOOK_SECRET_MISSING,
                            "sem segredo de webhook no cofre para o canal " + channel + " (fail-closed)");
                }
                logCredentialAccess(tenantId, channel, now);
                if (!WebhookSecurity.verifyHmacSignature(secret.get(), rawPayload, signatureHeader)) {
                    return reject(tenantId, channel, envelope, now, "assinatura do webhook não confere");
                }
            }
            case TELEGRAM_SECRET_TOKEN -> {
                Optional<String> secret = vault.webhookSecret(tenantId, channel);
                if (secret.isEmpty()) {
                    return refuse(tenantId, channel, envelope, now, Outcome.WEBHOOK_SECRET_MISSING,
                            "sem segredo de webhook no cofre para o canal " + channel + " (fail-closed)");
                }
                logCredentialAccess(tenantId, channel, now);
                if (!WebhookSecurity.verifySecretToken(secret.get(), secretHeader)) {
                    return reject(tenantId, channel, envelope, now, "segredo do webhook do Telegram não confere");
                }
            }
        }
        // (4) idempotência
        if (webhookEvents.alreadyProcessed(tenantId, channel, envelope.providerEventId())) {
            return new InboundResult(Outcome.DUPLICATE_IGNORED, null,
                    "evento do provedor já processado: " + envelope.providerEventId());
        }
        // (5) identidade resolvida
        if (envelope.protocolId() == null || envelope.consumerUnitId() == null || envelope.customerId() == null) {
            return new InboundResult(Outcome.IDENTITY_UNRESOLVED, null,
                    "borda não resolveu protocolo/matrícula/morador — conversa órfã recusada");
        }
        // (6) conversa + mensagem + integridade + medição
        appendIntegrity(tenantId, IntegrityLog.EventType.WEBHOOK_VERIFIED,
                channel + ":" + envelope.providerEventId(), "webhook", now);
        Optional<Conversation> existing = store.findOpenByThread(tenantId, channel, envelope.externalThreadId());
        Conversation conversation;
        if (existing.isPresent()) {
            conversation = existing.get().markCustomerMessage();
            store.update(conversation);
        } else {
            conversation = Conversation.open(UUID.randomUUID(), tenantId, envelope.protocolId(),
                    envelope.consumerUnitId(), envelope.customerId(), channel, envelope.externalThreadId(),
                    envelope.priorityProfile(), envelope.occurredAt());
            store.save(conversation);
            appendIntegrity(tenantId, IntegrityLog.EventType.CONVERSATION_OPENED,
                    conversation.id().toString(), "webhook", now);
        }
        ConversationMessage message = new ConversationMessage(UUID.randomUUID(), tenantId, conversation.id(),
                ConversationMessage.Direction.INBOUND, ConversationMessage.Author.CUSTOMER, envelope.body(),
                envelope.externalMessageId(), null, null, envelope.occurredAt());
        store.appendMessage(message);
        webhookEvents.markProcessed(tenantId, channel, envelope.providerEventId(), now);
        appendIntegrity(tenantId, IntegrityLog.EventType.MESSAGE_RECORDED,
                message.id().toString(), "webhook", now);
        usageStore.append(new UsageRecord(tenantId, MODULE_CODE, UsageMetric.MESSAGES, 1, null,
                message.id(), now));
        return new InboundResult(Outcome.ACCEPTED, conversation.id(), "mensagem registrada no protocolo "
                + envelope.protocolId());
    }

    /**
     * Recusa PRECOCE (antes da conferência do webhook): também é fato
     * auditável e entra na cadeia de integridade como
     * {@code REFUSAL_RECORDED}, com a referência canal:evento:motivo —
     * NUNCA o segredo (a falha de segredo ausente é de segurança e não
     * pode ficar sem rastro verificável).
     */
    private InboundResult refuse(UUID tenantId, OmnichannelChannel channel,
                                 OmnichannelPorts.InboundEnvelope envelope, Instant now,
                                 Outcome outcome, String reason) {
        appendIntegrity(tenantId, IntegrityLog.EventType.REFUSAL_RECORDED,
                channel + ":" + envelope.providerEventId() + ":" + outcome, "webhook", now);
        return new InboundResult(outcome, null, reason);
    }

    private InboundResult reject(UUID tenantId, OmnichannelChannel channel,
                                 OmnichannelPorts.InboundEnvelope envelope, Instant now, String reason) {
        appendIntegrity(tenantId, IntegrityLog.EventType.WEBHOOK_REJECTED,
                channel + ":" + envelope.providerEventId(), "webhook", now);
        return new InboundResult(Outcome.WEBHOOK_REJECTED, null, reason);
    }

    private void logCredentialAccess(UUID tenantId, OmnichannelChannel channel, Instant now) {
        String reference = vault.credentialReference(tenantId, channel)
                .orElse(channel + ":webhook-secret");
        appendIntegrity(tenantId, IntegrityLog.EventType.CREDENTIAL_ACCESSED, reference, "webhook", now);
    }

    private void appendIntegrity(UUID tenantId, IntegrityLog.EventType type, String reference,
                                 String actor, Instant at) {
        integrityLog.append(IntegrityLog.append(integrityLog.nextSequence(tenantId), tenantId, type,
                reference, actor, at, integrityLog.currentHash(tenantId)));
    }
}
