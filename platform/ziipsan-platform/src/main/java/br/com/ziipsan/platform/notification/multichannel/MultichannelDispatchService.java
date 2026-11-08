package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.notification.BusinessEventCatalog;
import br.com.ziipsan.platform.notification.ConsentGate;
import br.com.ziipsan.platform.notification.MessageTemplateEngine;
import br.com.ziipsan.platform.notification.MessagingParameters;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.NotificationDispatchRule;
import br.com.ziipsan.platform.notification.NotificationParameters;
import br.com.ziipsan.platform.notification.NotificationPolicy;
import br.com.ziipsan.platform.notification.NotificationPreference;
import br.com.ziipsan.platform.notification.OutboxMessage;
import br.com.ziipsan.platform.notification.PreferenceReader;
import br.com.ziipsan.platform.notification.RenderedMessage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Notificações multicanal (ED-41): dispara os avisos dos eventos do catálogo
 * da P1.38 por SMS, WhatsApp e push — os canais que a P3.5 declarou como
 * fronteira dela ({@code FRONTIER_ED41}) — com opt-in por canal e
 * finalidade, modelos da P2.30, remetente/conta por tenant, entregabilidade
 * medida e teto mensal. É o canal que a ED-20 (aviso "a caminho" da OS) e a
 * ED-17 (alertas de consumo da telemetria) consomem, pelo mesmo
 * {@link #dispatch} genérico por evento.
 *
 * <p>Ordem das guardas do disparo (a primeira que barra vence, e o motivo
 * volta nomeado no {@link MultichannelDispatchResult}):
 * <ol>
 *   <li>IN_APP volta à central da P3.36; EMAIL volta à P3.5 (a fatura por
 *       e-mail tem serviço próprio nesta entrega, {@link BillEmailService});</li>
 *   <li>canal fora de {@code msg.channels_enabled} do tenant → não dispara;</li>
 *   <li>sem provedor aprovado no DPA para o canal → não dispara;</li>
 *   <li>opt-in ({@link OptInRule} sobre o registro da P3.36): categoria não
 *       obrigatória sem opt-in positivo, ou com linha desligada, não
 *       dispara; categoria obrigatória está fora do alcance da
 *       preferência;</li>
 *   <li>opt-in de CONSUMIDOR exige o consentimento EXATO da linha vigente
 *       na P1.3 ({@link ConsentGate}) — DECISÃO registrada (D5): o recorte
 *       é SÓ este caminho ({@code ALLOWED_OPT_IN} de consumidor); categoria
 *       obrigatória e destinatário-usuário seguem o piso da P3.36, sem a
 *       checagem exata (fundamento no ponto da checagem e no relatório);</li>
 *   <li>sem contato verificado do canal no cadastro → não dispara;</li>
 *   <li>sem remetente de SMS / conta de WhatsApp do tenant → não dispara;</li>
 *   <li>WhatsApp sem classificação em {@code msg.template_category_map} →
 *       não dispara; com risco de marketing sem aprovação registrada →
 *       não dispara;</li>
 *   <li>canal pago sem preço vigente do provedor → não dispara; sem
 *       {@code msg.monthly_cap} configurado → não dispara; projeção acima
 *       do teto → não dispara;</li>
 *   <li>monta pelo motor da P2.30, envia pela porta do canal, grava a
 *       tentativa {@code SENT} e mede o uso — e SÓ ENTÃO, sobre o gasto
 *       efetivamente comprometido, emite os alertas de travessia de
 *       80%/100% via {@link CapAlertSink} (correção D3: envio bloqueado
 *       pelo teto não emite alerta de travessia que não ocorreu, e falha
 *       do gateway não deixa alerta sem gasto correspondente).</li>
 * </ol>
 *
 * <p>O mês do teto é o mês civil em UTC nesta entrega — o fuso do tenant
 * entra na fiação da integração (pendência nomeada no relatório).
 */
public class MultichannelDispatchService {

    public enum Outcome {
        SENT,
        DELEGATED_TO_CENTRAL,
        DELEGATED_TO_P35,
        CHANNEL_DISABLED,
        PROVIDER_NOT_IN_DPA,
        OPT_IN_MISSING,
        PREFERENCE_OFF,
        CONSENT_INACTIVE,
        NO_CONTACT,
        NO_SENDER_IDENTITY,
        TEMPLATE_NOT_CLASSIFIED,
        MARKETING_APPROVAL_REQUIRED,
        PRICE_NOT_CONFIGURED,
        MONTHLY_CAP_NOT_CONFIGURED,
        MONTHLY_CAP_REACHED
    }

    public record MultichannelDispatchResult(Outcome outcome, UUID deliveryId, BigDecimal estimatedCost,
                                             WhatsAppCategory category,
                                             MonthlyCapRule.CapDecision capDecision, String reason) {
        public boolean sent() {
            return outcome == Outcome.SENT;
        }

        static MultichannelDispatchResult of(Outcome outcome, String reason) {
            return new MultichannelDispatchResult(outcome, null, null, null, null, reason);
        }
    }

    private final BusinessEventCatalog catalog;
    private final MessagingParameters messagingParameters;
    private final PreferenceReader preferences;
    private final ConsentGate consentGate;
    private final NotificationParameters notificationParameters;
    private final ChannelContactRegistry contacts;
    private final MessageTemplateEngine templateEngine;
    private final ChannelGateways.SmsGateway smsGateway;
    private final ChannelGateways.WhatsAppGateway whatsAppGateway;
    private final ChannelGateways.PushGateway pushGateway;
    private final ChannelDeliveryStore store;
    private final ChannelPricing pricing;
    private final MultichannelParameters parameters;
    private final MultichannelRegistries.DpaProviderRegistry dpaRegistry;
    private final MultichannelRegistries.TemplateApprovalRegistry approvalRegistry;
    private final CapAlertSink capAlertSink;
    private final UsageStore usageStore;
    private final Clock clock;

    public MultichannelDispatchService(BusinessEventCatalog catalog, MessagingParameters messagingParameters,
                                       PreferenceReader preferences, ConsentGate consentGate,
                                       NotificationParameters notificationParameters,
                                       ChannelContactRegistry contacts, MessageTemplateEngine templateEngine,
                                       ChannelGateways.SmsGateway smsGateway,
                                       ChannelGateways.WhatsAppGateway whatsAppGateway,
                                       ChannelGateways.PushGateway pushGateway,
                                       ChannelDeliveryStore store, ChannelPricing pricing,
                                       MultichannelParameters parameters,
                                       MultichannelRegistries.DpaProviderRegistry dpaRegistry,
                                       MultichannelRegistries.TemplateApprovalRegistry approvalRegistry,
                                       CapAlertSink capAlertSink, UsageStore usageStore, Clock clock) {
        if (catalog == null || messagingParameters == null || preferences == null || consentGate == null
                || notificationParameters == null || contacts == null || templateEngine == null
                || smsGateway == null || whatsAppGateway == null || pushGateway == null || store == null
                || pricing == null || parameters == null || dpaRegistry == null || approvalRegistry == null
                || capAlertSink == null || usageStore == null || clock == null) {
            throw new IllegalArgumentException("catálogo, parâmetros, preferências, consentimento, cadastro, "
                    + "motor de modelos, gateways, persistência, preço, registros, alertas, medição e relógio "
                    + "são obrigatórios");
        }
        this.catalog = catalog;
        this.messagingParameters = messagingParameters;
        this.preferences = preferences;
        this.consentGate = consentGate;
        this.notificationParameters = notificationParameters;
        this.contacts = contacts;
        this.templateEngine = templateEngine;
        this.smsGateway = smsGateway;
        this.whatsAppGateway = whatsAppGateway;
        this.pushGateway = pushGateway;
        this.store = store;
        this.pricing = pricing;
        this.parameters = parameters;
        this.dpaRegistry = dpaRegistry;
        this.approvalRegistry = approvalRegistry;
        this.capAlertSink = capAlertSink;
        this.usageStore = usageStore;
        this.clock = clock;
    }

    /**
     * Dispara um aviso da outbox por UM canal multicanal. Evento fora do
     * catálogo ou carga inválida propaga a recusa da regra da P3.5
     * ({@link NotificationDispatchRule}) — nenhum disparo nasce.
     * {@code moduleCode} é o módulo produtor do aviso, gravado no uso medido.
     */
    public MultichannelDispatchResult dispatch(OutboxMessage message, NotificationChannel channel,
                                               String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            throw new IllegalArgumentException("o módulo produtor do aviso é obrigatório (uso medido por módulo)");
        }
        NotificationDispatchRule.DispatchDraft draft = NotificationDispatchRule.draft(message, catalog);
        UUID tenantId = draft.tenantId();

        if (channel == NotificationChannel.IN_APP) {
            return MultichannelDispatchResult.of(Outcome.DELEGATED_TO_CENTRAL,
                    "a linha IN_APP nasce na central de notificações da P3.36");
        }
        if (channel == NotificationChannel.EMAIL) {
            return MultichannelDispatchResult.of(Outcome.DELEGATED_TO_P35,
                    "e-mail é da P3.5; a fatura por e-mail é do BillEmailService da ED-41");
        }
        if (!messagingParameters.channelsEnabled(tenantId).contains(channel)) {
            return MultichannelDispatchResult.of(Outcome.CHANNEL_DISABLED,
                    "canal fora de msg.channels_enabled do tenant");
        }
        if (!dpaRegistry.providerApproved(tenantId, channel)) {
            return MultichannelDispatchResult.of(Outcome.PROVIDER_NOT_IN_DPA,
                    "sem provedor aprovado no DPA do tenant para este canal");
        }

        List<NotificationPreference> rows = preferences.find(tenantId, draft.recipientCustomerId(),
                draft.recipientUserId(), draft.purposeCode(), draft.category(), channel);
        boolean mandatory = NotificationPolicy.isMandatory(draft.category(),
                notificationParameters.tenantMandatoryCategories(tenantId));
        OptInRule.OptInDecision optIn = OptInRule.decide(mandatory, rows);
        if (optIn == OptInRule.OptInDecision.PREFERENCE_OFF) {
            return MultichannelDispatchResult.of(Outcome.PREFERENCE_OFF,
                    "preferência do destinatário desliga este canal para a finalidade");
        }
        if (optIn == OptInRule.OptInDecision.OPT_IN_MISSING) {
            return MultichannelDispatchResult.of(Outcome.OPT_IN_MISSING,
                    "sem opt-in registrado do destinatário para este canal e finalidade (P3.36/P1.3)");
        }
        // DECISÃO REGISTRADA — recorte do consentimento exato (defeito D5 da
        // revisão da ED-41; decisão também no relatório, seção de correção):
        // o ConsentGate EXATO da P1.3 é consultado SOMENTE neste caminho —
        // opt-in de CONSUMIDOR (ALLOWED_OPT_IN com recipientCustomerId).
        // Fundamento: (1) categoria obrigatória (ALLOWED_MANDATORY) está
        // FORA do alcance da preferência/consentimento pelo piso da P3.36
        // (NotificationPolicy: aviso legal/cobrança obrigatória não se
        // desliga por preferência — exigir o consentimento exato aqui
        // travaria aviso que a lei manda entregar); (2) destinatário-USUÁRIO
        // (recipientUserId, sem customerId) é sujeito interno da plataforma,
        // não titular de consentimento de consumidor da P1.3 — o ConsentGate
        // é indexado por consumidor/contato/consentimento dele. O recorte é
        // MAIS estrito que a P3.5 (que não consulta consentimento no disparo
        // de e-mail) e coerente com o piso da P3.36. Nota de integração
        // (mesmo padrão da P3.5): o contato resolvido adiante NÃO é amarrado
        // ao contactId desta linha de preferência — a resolução é
        // independente, por finalidade, no cadastro da P1.3.
        if (optIn == OptInRule.OptInDecision.ALLOWED_OPT_IN && draft.recipientCustomerId() != null) {
            NotificationPreference row = rows.stream().filter(NotificationPreference::enabled)
                    .findFirst().orElse(null);
            if (row == null || row.contactId() == null || row.consentId() == null
                    || !consentGate.hasActiveConsent(tenantId, draft.recipientCustomerId(),
                            row.contactId(), row.consentId(), draft.purposeCode())) {
                return MultichannelDispatchResult.of(Outcome.CONSENT_INACTIVE,
                        "o consentimento exato da preferência não está vigente na P1.3");
            }
        }

        ChannelContact contact = (channel == NotificationChannel.PUSH
                ? contacts.resolvePushContact(tenantId, draft.recipientCustomerId(), draft.recipientUserId(),
                        draft.purposeCode())
                : contacts.resolvePhoneContact(tenantId, draft.recipientCustomerId(), draft.recipientUserId(),
                        draft.purposeCode()))
                .orElse(null);
        if (contact == null || !contact.verified()) {
            return MultichannelDispatchResult.of(Outcome.NO_CONTACT,
                    "sem contato verificado deste canal no cadastro para o destinatário e a finalidade");
        }

        String senderIdentity = null;
        if (channel == NotificationChannel.SMS) {
            senderIdentity = parameters.smsSenderId(tenantId).orElse(null);
            if (senderIdentity == null || senderIdentity.isBlank()) {
                return MultichannelDispatchResult.of(Outcome.NO_SENDER_IDENTITY,
                        "sem remetente autenticado de SMS do tenant (msg.sms_sender_id)");
            }
        } else if (channel == NotificationChannel.WHATSAPP) {
            senderIdentity = parameters.whatsappAccountId(tenantId).orElse(null);
            if (senderIdentity == null || senderIdentity.isBlank()) {
                return MultichannelDispatchResult.of(Outcome.NO_SENDER_IDENTITY,
                        "sem conta de WhatsApp do tenant (msg.whatsapp_account_id)");
            }
        }

        RenderedMessage rendered = templateEngine.render(tenantId, draft.templateCode(), channel,
                draft.variables());

        WhatsAppCategory category = null;
        if (channel == NotificationChannel.WHATSAPP) {
            category = TemplateCategoryRule.classify(parameters.templateCategoryMap(tenantId),
                    draft.templateCode()).orElse(null);
            if (category == null) {
                return MultichannelDispatchResult.of(Outcome.TEMPLATE_NOT_CLASSIFIED,
                        "modelo de WhatsApp sem categoria em msg.template_category_map");
            }
            boolean risk = TemplateCategoryRule.marketingRisk(category, draft.category(),
                    rendered.subject() + "\n" + rendered.body(),
                    TemplateCategoryRule.PROPOSED_MARKETING_TERMS);
            if (risk && !approvalRegistry.marketingRiskApproved(tenantId, rendered.templateCode(),
                    rendered.templateVersion())) {
                return new MultichannelDispatchResult(Outcome.MARKETING_APPROVAL_REQUIRED, null, null,
                        category, null,
                        "modelo de cobrança com texto de risco de classificação como marketing: "
                                + "alerta emitido; envio aguarda a aprovação registrada do risco");
            }
        }

        BigDecimal estimatedCost = null;
        MonthlyCapRule.CapDecision capDecision = null;
        BigDecimal capValue = null;
        if (channel == NotificationChannel.SMS || channel == NotificationChannel.WHATSAPP) {
            Optional<BigDecimal> unitPrice = pricing.unitPrice(tenantId, channel, category);
            if (unitPrice.isEmpty()) {
                return new MultichannelDispatchResult(Outcome.PRICE_NOT_CONFIGURED, null, null, category,
                        null, "sem preço vigente do provedor para este canal/categoria: estimativa impossível");
            }
            estimatedCost = TemplateCategoryRule.estimate(unitPrice.get(), 1);
            Optional<BigDecimal> cap = parameters.monthlyCap(tenantId);
            if (cap.isEmpty()) {
                return new MultichannelDispatchResult(Outcome.MONTHLY_CAP_NOT_CONFIGURED, null, estimatedCost,
                        category, null, "msg.monthly_cap não configurado para o tenant: disparo pago recusado");
            }
            capValue = cap.get();
            Instant now = clock.instant();
            BigDecimal spent = store.sumEstimatedCost(tenantId, monthStart(now), now);
            capDecision = MonthlyCapRule.evaluate(capValue, spent, estimatedCost);
            // CORREÇÃO D3: NENHUM alerta é emitido aqui. A decisão da regra é
            // sobre a PROJEÇÃO; o alerta só nasce depois, sobre o gasto
            // efetivamente comprometido (envio aceito e tentativa gravada —
            // ver o fim deste método). Envio bloqueado pelo teto devolve sem
            // alertar uma travessia que não ocorreu.
            if (capDecision.blocked()) {
                return new MultichannelDispatchResult(Outcome.MONTHLY_CAP_REACHED, null, estimatedCost,
                        category, capDecision,
                        "a projeção do mês ultrapassa msg.monthly_cap do tenant");
            }
        }

        String providerMessageId = switch (channel) {
            case SMS -> smsGateway.send(tenantId, senderIdentity, contact.destination(), rendered);
            case WHATSAPP -> whatsAppGateway.send(tenantId, senderIdentity, contact.destination(), rendered,
                    category);
            case PUSH -> pushGateway.send(tenantId, contact.destination(), rendered);
            default -> throw new IllegalStateException("canal sem gateway multicanal: " + channel);
        };
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalStateException("o gateway do canal não devolveu o id da mensagem do provedor");
        }
        Instant sentAt = clock.instant();
        ChannelDelivery delivery = new ChannelDelivery(UUID.randomUUID(), tenantId, draft.noticeId(),
                draft.recipientCustomerId(), draft.recipientUserId(), contact.contactId(), channel,
                contact.destination(), rendered.templateCode(), rendered.templateVersion(), category,
                ChannelDeliveryStatus.SENT, providerMessageId, estimatedCost, sentAt, null);
        store.save(delivery);
        usageStore.append(new UsageRecord(tenantId, moduleCode, UsageMetric.MESSAGES, 1,
                estimatedCost, delivery.id(), sentAt));
        if (channel == NotificationChannel.SMS) {
            usageStore.append(new UsageRecord(tenantId, moduleCode, UsageMetric.SMS, 1,
                    estimatedCost, delivery.id(), sentAt));
        }
        // CORREÇÃO D3: os alertas de teto são emitidos AQUI — depois da
        // decisão de envio e com a tentativa já gravada, ou seja, sobre o
        // gasto efetivamente comprometido (spentBefore + estimatedCost deste
        // envio = projectedSpend da decisão, agora realizado). Se o gateway
        // lançou exceção ou devolveu id vazio acima, a execução não chega
        // aqui: não há alerta sem gasto. O valor informado ao sink é o gasto
        // comprometido após este envio, no instante do envio.
        if (capDecision != null) {
            if (capDecision.alert80Raised()) {
                capAlertSink.onCapAlert(tenantId, MonthlyCapRule.CapLevel.ALERT_80,
                        capDecision.projectedSpend(), capValue, sentAt);
            }
            if (capDecision.alert100Raised()) {
                capAlertSink.onCapAlert(tenantId, MonthlyCapRule.CapLevel.REACHED_100,
                        capDecision.projectedSpend(), capValue, sentAt);
            }
        }
        return new MultichannelDispatchResult(Outcome.SENT, delivery.id(), estimatedCost, category,
                capDecision, null);
    }

    /** Dispara o aviso por um conjunto de canais; um resultado por canal (uso da ED-20/ED-17). */
    public Map<NotificationChannel, MultichannelDispatchResult> dispatchToChannels(
            OutboxMessage message, Set<NotificationChannel> channels, String moduleCode) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("os canais do disparo são obrigatórios");
        }
        Map<NotificationChannel, MultichannelDispatchResult> results =
                new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : channels) {
            results.put(channel, dispatch(message, channel, moduleCode));
        }
        return Map.copyOf(results);
    }

    /**
     * Aplica um evento do provedor ({@code delivered/failed/rejected}):
     * resolve a tentativa, grava a situação e, na REJEIÇÃO, marca o contato
     * como inválido no cadastro da P1.3. Idempotente pelo id do evento do
     * provedor; tentativa já resolvida não regride.
     */
    public ChannelDelivery onChannelEvent(ChannelDeliveryEvent event) {
        if (store.eventAlreadyProcessed(event.tenantId(), event.providerEventId())) {
            return store.findByProviderMessageId(event.tenantId(), event.providerMessageId())
                    .orElseThrow(() -> new ApiException.NotFound(
                            "tentativa multicanal do evento já processado não encontrada"));
        }
        ChannelDelivery attempt = store.findByProviderMessageId(event.tenantId(), event.providerMessageId())
                .orElseThrow(() -> new ApiException.NotFound(
                        "tentativa multicanal não encontrada para o evento do provedor"));
        if (attempt.resolvedAt() != null) {
            store.markEventProcessed(event.tenantId(), event.providerEventId(), event.occurredAt());
            return attempt;
        }
        ChannelDeliveryStatus status = switch (event.type()) {
            case DELIVERED -> ChannelDeliveryStatus.DELIVERED;
            case FAILED -> ChannelDeliveryStatus.FAILED;
            case REJECTED -> ChannelDeliveryStatus.REJECTED;
        };
        ChannelDelivery updated = attempt.resolved(status, event.occurredAt());
        store.update(updated);
        if (event.type() == ChannelDeliveryEvent.ChannelEventType.REJECTED) {
            contacts.markContactInvalid(event.tenantId(), attempt.contactId(),
                    event.reason() != null ? event.reason() : "provider-rejected");
        }
        store.markEventProcessed(event.tenantId(), event.providerEventId(), event.occurredAt());
        return updated;
    }

    /**
     * Entregabilidade medida do tenant no canal e período, das tentativas
     * registradas. A janela é a COORTE DE ENVIO ({@code sentAt}), única para
     * envios e resolvidas — contrato da porta {@link ChannelDeliveryStore}
     * (correção D4): mensagem enviada antes da janela e resolvida dentro
     * dela NÃO entra em nenhuma das contagens desta janela.
     */
    public DeliverabilityRule.DeliverabilitySnapshot deliverability(
            UUID tenantId, NotificationChannel channel, Instant from, Instant to) {
        return DeliverabilityRule.snapshot(tenantId, channel, from, to,
                store.countSent(tenantId, channel, from, to),
                store.countWithStatus(tenantId, channel, ChannelDeliveryStatus.DELIVERED, from, to),
                store.countWithStatus(tenantId, channel, ChannelDeliveryStatus.FAILED, from, to),
                store.countWithStatus(tenantId, channel, ChannelDeliveryStatus.REJECTED, from, to));
    }

    private static Instant monthStart(Instant now) {
        return YearMonth.from(now.atOffset(ZoneOffset.UTC)).atDay(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
