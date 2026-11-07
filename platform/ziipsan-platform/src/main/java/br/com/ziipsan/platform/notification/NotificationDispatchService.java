package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Notificações externas com prova de entrega (P3.5): dispara os avisos dos eventos
 * do catálogo da P1.38 pelos canais do tenant, com a mensagem montada pelo motor da
 * P2.30, e registra a prova a partir dos eventos do provedor de e-mail da P0.45.
 *
 * <p>O tenant é EXPLÍCITO em cada chamada (o worker da P1.9 recebe o tenant na
 * mensagem e abre o contexto a cada execução — nenhum estado deste serviço é
 * global): pausa, supressão, reputação e métricas são lidas e gravadas sempre
 * com o tenant da operação, de modo que um tenant com lista ruim não interrompe
 * o envio dos demais (teste dedicado).
 *
 * <p>Ordem das guardas do disparo por e-mail (a primeira que barra vence, e o
 * motivo volta nomeado no {@link DispatchResult}):
 * <ol>
 *   <li>canal fora de {@code msg.channels_enabled} (classe d) → não dispara;</li>
 *   <li>tenant pausado → não dispara (só este tenant);</li>
 *   <li>taxa de reclamação do tenant em 0,10% ou mais → pausa o tenant e não dispara;</li>
 *   <li>preferência da P3.36 desliga o canal para a finalidade → não dispara,
 *       EXCETO categoria obrigatória: aviso legal está fora do alcance da
 *       preferência (piso da {@link NotificationPolicy});</li>
 *   <li>sem contato de e-mail verificado no cadastro da P1.3 → não dispara;</li>
 *   <li>endereço suprimido neste tenant → não dispara;</li>
 *   <li>domínio do tenant sem SPF/DKIM/DMARC alinhados → não dispara;</li>
 *   <li>monta pelo motor da P2.30 e envia pelo gateway da P0.45, gravando a
 *       tentativa {@code SENT} com o id do provedor.</li>
 * </ol>
 *
 * <p>Fronteiras do aceite, sem fingimento: o provedor é da LC.2 (só a interface
 * {@link EmailGateway} é modelada); SMS, WhatsApp, push e custo por mensagem são
 * da ED-41 — canal dessas famílias devolve {@code FRONTIER_ED41}, nunca um envio
 * simulado; o remetente autenticado de SMS/voz (MP 1.396/2026) é regra aplicada
 * pela ED-41; a linha IN_APP nasce na central da P3.36 ({@code DELEGATED_TO_CENTRAL}).
 */
public class NotificationDispatchService {

    /** Modelo do alerta preventivo de consumo anormal (modelo de fábrica da P2.30, ajustável pelo tenant). */
    public static final String HIGH_CONSUMPTION_TEMPLATE = "alert.high-consumption";
    /** Variável do modelo com o código da orientação de teste de vazamento. */
    public static final String LEAK_TEST_GUIDANCE_CODE = "guidance.leak-test";
    /** Variável do modelo com o código do atalho para pedir revisão da conta por protocolo (P1.36). */
    public static final String BILL_REVIEW_ACTION_CODE = "action.request-bill-review";
    /** Finalidade do alerta preventivo no catálogo de finalidades da P1.3. */
    public static final String HIGH_CONSUMPTION_PURPOSE = "HIGH_CONSUMPTION_ALERT";

    private final DeliveryStore store;
    private final EmailGateway emailGateway;
    private final MessageTemplateEngine templateEngine;
    private final BusinessEventCatalog catalog;
    private final PreferenceReader preferences;
    private final ContactRegistry contacts;
    private final MessagingParameters parameters;
    private final NotificationParameters notificationParameters;
    private final Clock clock;

    public NotificationDispatchService(DeliveryStore store, EmailGateway emailGateway,
                                       MessageTemplateEngine templateEngine, BusinessEventCatalog catalog,
                                       PreferenceReader preferences, ContactRegistry contacts,
                                       MessagingParameters parameters,
                                       NotificationParameters notificationParameters, Clock clock) {
        if (store == null || emailGateway == null || templateEngine == null || catalog == null
                || preferences == null || contacts == null || parameters == null
                || notificationParameters == null || clock == null) {
            throw new IllegalArgumentException("persistência, gateway, motor de modelos, catálogo, preferências, "
                    + "cadastro, parâmetros e relógio são obrigatórios");
        }
        this.store = store;
        this.emailGateway = emailGateway;
        this.templateEngine = templateEngine;
        this.catalog = catalog;
        this.preferences = preferences;
        this.contacts = contacts;
        this.parameters = parameters;
        this.notificationParameters = notificationParameters;
        this.clock = clock;
    }

    /**
     * Dispara um aviso da outbox por UM canal. Evento fora do catálogo ou carga
     * inválida propaga a recusa da {@link NotificationDispatchRule} (nenhum
     * disparo nasce). Canal IN_APP não é deste serviço: {@code DELEGATED_TO_CENTRAL}.
     */
    public DispatchResult dispatch(OutboxMessage message, NotificationChannel channel) {
        NotificationDispatchRule.DispatchDraft draft = NotificationDispatchRule.draft(message, catalog);
        return dispatchDraft(draft, channel, draft.variables());
    }

    /** Dispara o aviso por todos os canais ligados do tenant; um resultado por canal. */
    public Map<NotificationChannel, DispatchResult> dispatchAllChannels(OutboxMessage message) {
        NotificationDispatchRule.DispatchDraft draft = NotificationDispatchRule.draft(message, catalog);
        Map<NotificationChannel, DispatchResult> results = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : NotificationChannel.values()) {
            results.put(channel, dispatchDraft(draft, channel, draft.variables()));
        }
        return Map.copyOf(results);
    }

    private DispatchResult dispatchDraft(NotificationDispatchRule.DispatchDraft draft,
                                         NotificationChannel channel, Map<String, String> variables) {
        UUID tenantId = draft.tenantId();
        if (channel == NotificationChannel.IN_APP) {
            return DispatchResult.of(DispatchResult.Outcome.DELEGATED_TO_CENTRAL,
                    "a linha IN_APP nasce na central de notificações da P3.36");
        }
        if (channel != NotificationChannel.EMAIL) {
            return DispatchResult.of(DispatchResult.Outcome.FRONTIER_ED41,
                    "canal da ED-41 (SMS, WhatsApp, push): não enviado por esta tarefa");
        }
        if (!parameters.channelsEnabled(tenantId).contains(channel)) {
            return DispatchResult.of(DispatchResult.Outcome.CHANNEL_DISABLED,
                    "canal fora de msg.channels_enabled do tenant");
        }
        DeliveryStore.TenantSendingState state = store.sendingState(tenantId);
        if (state.paused()) {
            return DispatchResult.of(DispatchResult.Outcome.TENANT_PAUSED,
                    "envio do tenant pausado: " + state.pauseReason());
        }
        if (complaintRateBreached(tenantId)) {
            store.setPaused(tenantId, true, "complaint-rate");
            return DispatchResult.of(DispatchResult.Outcome.COMPLAINT_RATE_BREACH,
                    "taxa de reclamação do tenant atingiu 0,10%: tenant pausado, os demais não são afetados");
        }
        if (preferenceDisables(draft, channel)) {
            return DispatchResult.of(DispatchResult.Outcome.PREFERENCE_OFF,
                    "preferência do destinatário desliga este canal para a finalidade");
        }
        RecipientContact contact = contacts.resolveEmailContact(
                        tenantId, draft.recipientCustomerId(), draft.recipientUserId(), draft.purposeCode())
                .orElse(null);
        if (contact == null || !contact.verified()) {
            return DispatchResult.of(DispatchResult.Outcome.NO_CONTACT,
                    "sem contato de e-mail verificado no cadastro para o destinatário e a finalidade");
        }
        String address = normalize(contact.emailAddress());
        if (store.isSuppressed(tenantId, address)) {
            return DispatchResult.of(DispatchResult.Outcome.SUPPRESSED,
                    "endereço na lista de supressão do tenant");
        }
        String fromDomain = parameters.emailFromDomain(tenantId);
        SenderDomainStatus domainStatus = emailGateway.domainStatus(tenantId, fromDomain);
        if (domainStatus == null) {
            // Sem o estado do domínio não há como provar o alinhamento: não envia,
            // com o motivo nomeado, em vez de a recusa virar exceção crua da regra
            // (correção da revisão cruzada).
            return DispatchResult.of(DispatchResult.Outcome.DOMAIN_NOT_ALIGNED,
                    "o provedor não devolveu o estado de autenticação do domínio do tenant");
        }
        try {
            DeliveryProofRule.assertDomainAligned(fromDomain, domainStatus);
        } catch (ApiException.Validation e) {
            return DispatchResult.of(DispatchResult.Outcome.DOMAIN_NOT_ALIGNED, e.getMessage());
        }
        RenderedMessage rendered = templateEngine.render(tenantId, draft.templateCode(), channel, variables);
        String providerMessageId = emailGateway.send(tenantId, fromDomain, address, rendered);
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalStateException("o gateway de e-mail não devolveu o id da mensagem do provedor");
        }
        DeliveryAttempt attempt = new DeliveryAttempt(UUID.randomUUID(), tenantId, draft.noticeId(),
                draft.recipientCustomerId(), draft.recipientUserId(), contact.contactId(), channel, address,
                rendered.templateCode(), rendered.templateVersion(), DeliveryStatus.SENT,
                providerMessageId, clock.instant(), null);
        store.saveAttempt(attempt);
        return DispatchResult.sent(attempt.id());
    }

    /**
     * A preferência desliga o canal? Combina as linhas da finalidade/categoria/canal
     * pela regra mais restritiva (como o plano de ingestão da P3.36): qualquer linha
     * desligada desliga. Categoria obrigatória (piso ∪ tenant, via
     * {@link NotificationPolicy}) está FORA do alcance da preferência — em especial
     * o aviso legal: mesmo com linha desligada gravada (que a guarda de gravação da
     * P3.36 já recusa), o disparo acontece.
     */
    private boolean preferenceDisables(NotificationDispatchRule.DispatchDraft draft, NotificationChannel channel) {
        // Categorias obrigatórias: piso da plataforma ∪ acréscimos do tenant em
        // notify.mandatory_categories, lidos pela porta da P3.36 (fonte única).
        Set<NotificationCategory> tenantMandatory = notificationParameters.tenantMandatoryCategories(draft.tenantId());
        if (NotificationPolicy.isMandatory(draft.category(), tenantMandatory)) {
            return false;
        }
        List<NotificationPreference> rows = preferences.find(draft.tenantId(), draft.recipientCustomerId(),
                draft.recipientUserId(), draft.purposeCode(), draft.category(), channel);
        return rows.stream().anyMatch(row -> !row.enabled());
    }

    /**
     * Aplica um evento do provedor de e-mail ({@code delivered/bounced/complained}):
     * resolve a tentativa, grava a nova situação e, na rejeição ou na reclamação,
     * marca o contato como inválido no cadastro da P1.3, abre a pendência e
     * suprime o endereço conforme a política do tenant. Evento repetido (mesmo
     * {@code providerEventId}) NÃO repete efeito nenhum, e tentativa já resolvida
     * não regride: vale o primeiro evento terminal, salvo {@code DELIVERED} →
     * {@code COMPLAINED} (correção da revisão cruzada). Evento de mensagem
     * desconhecida neste tenant é erro explícito — nunca é ignorado em silêncio.
     * Na reclamação, se a taxa do tenant atingir 0,10%, o tenant é pausado
     * (só ele). Devolve a tentativa resolvida.
     */
    public DeliveryAttempt onEmailEvent(EmailDeliveryEvent event) {
        DeliveryAttempt attempt = store.findByProviderMessageId(event.tenantId(), event.providerMessageId())
                .orElseThrow(() -> new ApiException.NotFound(
                        "Nenhuma tentativa de entrega deste tenant tem a mensagem do evento."));
        if (store.eventAlreadyProcessed(event.tenantId(), event.providerEventId())) {
            return attempt;
        }
        DeliveryStatus newStatus = switch (event.type()) {
            case DELIVERED -> DeliveryStatus.DELIVERED;
            case BOUNCED -> DeliveryStatus.BOUNCED;
            case COMPLAINED -> DeliveryStatus.COMPLAINED;
        };
        // Tentativa já resolvida NÃO regride nem repete efeitos: vale o primeiro
        // evento terminal. Sem esta guarda, um delivered tardio depois de um bounce
        // sobrescreveria a situação e FABRICARIA a prova de um aviso não entregue
        // (liberando indevidamente a OS de corte), e um segundo evento terminal
        // abriria pendência duplicada no cadastro. Única transição posterior
        // admitida: DELIVERED -> COMPLAINED — a reclamação pressupõe a entrega e,
        // pela regra do aceite, conta como aviso não entregue e dispara os efeitos
        // no cadastro (correção da revisão cruzada).
        boolean alreadyResolved = attempt.status() != DeliveryStatus.SENT;
        boolean complaintAfterDelivery = attempt.status() == DeliveryStatus.DELIVERED
                && newStatus == DeliveryStatus.COMPLAINED;
        if (alreadyResolved && !complaintAfterDelivery) {
            store.markEventProcessed(event.tenantId(), event.providerEventId(), clock.instant());
            return attempt;
        }
        DeliveryAttempt resolved = attempt.resolved(newStatus, event.occurredAt());
        store.updateAttempt(resolved);
        if (event.type() == EmailEventType.BOUNCED || event.type() == EmailEventType.COMPLAINED) {
            String reason = event.type() == EmailEventType.BOUNCED
                    ? "email-bounced" : "email-complained";
            contacts.markContactInvalid(event.tenantId(), attempt.contactId(), reason);
            contacts.openInvalidContactPendency(event.tenantId(), attempt.customerId(), attempt.contactId(), reason);
            if (parameters.suppressionPolicy(event.tenantId()).suppresses(event.type())) {
                store.addSuppression(event.tenantId(), normalize(attempt.emailAddress()), event.type(), event.occurredAt());
            }
        }
        if (event.type() == EmailEventType.COMPLAINED && complaintRateBreached(event.tenantId())) {
            store.setPaused(event.tenantId(), true, "complaint-rate");
        }
        // O evento só é marcado como processado DEPOIS de todos os efeitos: se um
        // efeito falhar no meio, a reentrega do provedor tenta de novo em vez de o
        // evento já marcado suprimir os efeitos para sempre (correção da revisão).
        store.markEventProcessed(event.tenantId(), event.providerEventId(), clock.instant());
        return resolved;
    }

    /** Verdade se o aviso tem prova de entrega (alguma tentativa {@code DELIVERED}). */
    public boolean hasDeliveryProof(UUID tenantId, UUID noticeId) {
        return DeliveryProofRule.hasProof(store.attemptsForNotice(tenantId, noticeId));
    }

    /**
     * O portão da OS de corte (regra consumida pela P3.4): sem prova de entrega do
     * aviso de corte, recusa 409 — a OS não nasce nem executa.
     */
    public void assertCutoffAllowed(UUID tenantId, UUID noticeId) {
        DeliveryProofRule.assertCutoffAllowed(store.attemptsForNotice(tenantId, noticeId));
    }

    /**
     * Taxa de reclamação do tenant violada (≥ 0,10%)? Sem envios no período não há
     * taxa nem violação. O período é o histórico gravado do tenant (a janela de
     * apuração do provedor é decisão da LC.2 — declaração no relatório).
     */
    public boolean complaintRateBreached(UUID tenantId) {
        long sent = store.countSent(tenantId, Instant.EPOCH, clock.instant());
        if (sent == 0) {
            return false;
        }
        long complaints = store.countComplaints(tenantId, Instant.EPOCH, clock.instant());
        return DeliveryProofRule.complaintRateBreaches(sent, complaints);
    }

    /**
     * Alerta preventivo de consumo anormal, no fechamento da leitura: recebe o
     * consumo do ciclo e a média que a crítica da P2.5 calculou. Sem disparo da
     * crítica (consumo até o limite), NENHUM aviso nasce — devolve mapa vazio.
     * Com disparo, avisa ANTES da fatura pelos canais de {@code alert.channels}
     * (classe d), com a orientação de teste de vazamento e o atalho para pedir
     * revisão por protocolo nas variáveis do modelo. Canais de e-mail seguem o
     * mesmo caminho guardado do disparo comum (preferência da finalidade do
     * alerta, supressão, domínio, pausa — tudo do tenant).
     *
     * <p>O rascunho do alerta é montado aqui (não vem da outbox): o gatilho é a
     * crítica da P2.5 no fechamento da leitura. O nome de evento
     * {@code reading.closed} é a convenção desta tarefa para o catálogo da
     * P1.38 — a integração o declara no {@code vertical.json} do pacote, como a
     * P1.38 exige de todo evento consumido.
     */
    public Map<NotificationChannel, DispatchResult> onReadingClosed(
            UUID tenantId, UUID customerId, UUID consumerUnitId,
            BigDecimal consumption, BigDecimal averageConsumption) {
        if (tenantId == null || customerId == null || consumerUnitId == null) {
            throw new IllegalArgumentException("tenant, consumidor e unidade consumidora são obrigatórios no alerta");
        }
        BigDecimal pct = parameters.highConsumptionPct(tenantId);
        if (!HighConsumptionAlertRule.fires(consumption, averageConsumption, pct)) {
            return Map.of();
        }
        Map<String, String> variables = Map.of(
                "consumption", consumption.toPlainString(),
                "averageConsumption", averageConsumption.toPlainString(),
                "excessPct", HighConsumptionAlertRule.excessPct(consumption, averageConsumption).toPlainString(),
                "consumerUnitId", consumerUnitId.toString(),
                "leakTestGuidanceCode", LEAK_TEST_GUIDANCE_CODE,
                "billReviewActionCode", BILL_REVIEW_ACTION_CODE);
        NotificationDispatchRule.DispatchDraft draft = new NotificationDispatchRule.DispatchDraft(
                UUID.randomUUID(), tenantId, null, customerId, "reading.closed", 1,
                NotificationCategory.ACCOUNT, HIGH_CONSUMPTION_PURPOSE, HIGH_CONSUMPTION_TEMPLATE, variables);
        Map<NotificationChannel, DispatchResult> results = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : parameters.alertChannels(tenantId)) {
            results.put(channel, dispatchDraft(draft, channel, variables));
        }
        return Map.copyOf(results);
    }

    private static String normalize(String emailAddress) {
        return emailAddress.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
