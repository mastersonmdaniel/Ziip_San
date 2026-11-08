package br.com.ziipsan.platform.tenantalert;

import br.com.ziipsan.platform.notification.BusinessEventCatalog;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ED-07: monitoramento e alertas para o cliente — o serviço.
 *
 * <ol>
 *   <li><b>Configuração</b> ({@link #configureRule}): a escolha do tenant só
 *       nasce se o evento estiver no catálogo da P1.38, na versão declarada
 *       (porta real {@link BusinessEventCatalog} da P3.36). Fora do catálogo
 *       ou em versão não declarada: {@link AlertRejectedException} e NADA é
 *       gravado — o teste de recusa do aceite (item "evento fora do catálogo
 *       é recusado", também no CI quando a integração ligar este serviço).</li>
 *   <li><b>Evento</b> ({@link #onBusinessEvent}): evento do catálogo sem
 *       regra ativa do tenant NÃO gera alerta (não houve escolha) e não é
 *       erro — devolve {@code IGNORED_NO_RULE}. Evento FORA do catálogo é
 *       recusado também aqui (defesa em profundidade: a outbox da P1.9/P1.38
 *       já recusa na publicação; se uma mensagem escapar, o alerta não
 *       nasce). Para cada regra ativa: nasce UM {@link TenantAlert}, entregue
 *       na central de avisos da P3.36 ({@link TenantAlertPorts.NotificationCenterPort},
 *       ligada de verdade pelo {@link P336NotificationCenterAdapter}) e, se a
 *       regra pedir webhook e o tenant o tiver configurado, pela entrega
 *       confiável assinada da P2.26 ({@link ReliableWebhookDelivery}).
 *       Reentrega da mesma mensagem (pelo menos uma vez da outbox) devolve o
 *       alerta existente SEM repetir entregas — idempotência por
 *       (regra, id do evento).</li>
 *   <li><b>Falha de lote</b> ({@link #onBatchFailure}): a execução FAILED da
 *       P2.1 notifica o responsável do tenant resolvido pela porta
 *       {@link TenantAlertPorts.TenantResponsible}, pela porta TIPADA
 *       {@link TenantAlertPorts.BatchFailureNotificationPort} — NÃO pela
 *       ingestão da P3.36, que só aceita evento do catálogo (correção D1:
 *       a falha de lote não finge evento; a rota à central real nasce quando
 *       a integração declarar o evento, via
 *       {@code P336NotificationCenterAdapter.deliverBatchFailureWithDeclaredEvent}).
 *       Sem responsável configurado, o desfecho é {@code NO_RESPONSIBLE}
 *       declarado — nenhum destinatário é inventado. A falha repetida da
 *       mesma execução não duplica o alerta.</li>
 * </ol>
 *
 * <p><b>Ordem entrega → gravação (correção D4 pós-reprovação).</b> Nos dois
 * caminhos o alerta só é GRAVADO depois que as entregas (central e, quando
 * configurado, webhook; ou a porta de lote) retornam sem exceção. Se uma
 * entrega lançar exceção, nada fica gravado e a exceção propaga: a
 * reentrega da outbox NÃO encontra alerta "duplicado" fantasma e tenta a
 * entrega de verdade — o "pelo menos uma vez" vale também no caminho de
 * falha. Regras já entregues e gravadas antes da falha continuam
 * idempotentes na reentrega (devolvem {@code duplicate=true} sem repetir
 * entregas); a regra que falhou é a que é refeita.
 *
 * <p>Códigos de mensagem: convenção DESTA entrega, declarada — o título do
 * alerta de evento é {@code tenant-alert.<nome-do-evento>} e o corpo
 * {@code tenant-alert.<nome-do-evento>.body}; o da falha de lote é
 * {@code tenant-alert.batch-failed}. São códigos do catálogo de mensagens
 * (como os {@code titleCode}/{@code bodyCode} da P3.36), resolvidos em texto
 * pela camada de mensagens — nunca texto livre com dado pessoal.
 */
public final class TenantAlertService {

    public static final String BATCH_FAILURE_TITLE_CODE = "tenant-alert.batch-failed";
    public static final String BATCH_FAILURE_BODY_CODE = "tenant-alert.batch-failed.body";

    public enum EventOutcome { ALERTED, IGNORED_NO_RULE }

    /** Desfecho de um evento: o resultado por regra (vazia quando nenhuma regra escolheu o evento). */
    public record EventResult(EventOutcome outcome, List<RuleDelivery> deliveries) {
        public EventResult {
            deliveries = List.copyOf(deliveries);
        }
    }

    public record RuleDelivery(UUID ruleId, UUID alertId, boolean duplicate,
                               ReliableWebhookDelivery.Result webhook) {
    }

    public enum BatchOutcome { NOTIFIED, NO_RESPONSIBLE, DUPLICATE }

    public record BatchResult(BatchOutcome outcome, UUID alertId) {
    }

    /** Falha de lote como a P2.1 a registra: execução, ciclo e rotas falhadas — só identificadores e códigos. */
    public record BatchFailure(UUID tenantId, UUID runId, String cycleRef, List<String> failedRoutes,
                               String errorCode) {
        public BatchFailure {
            Objects.requireNonNull(tenantId, "a falha de lote exige o tenant");
            Objects.requireNonNull(runId, "a falha de lote exige a execução (job_runs)");
            if (cycleRef == null || cycleRef.isBlank()) {
                throw new IllegalArgumentException("a falha de lote exige o ciclo");
            }
            failedRoutes = failedRoutes == null ? List.of() : List.copyOf(failedRoutes);
        }
    }

    private final BusinessEventCatalog catalog;
    private final TenantAlertPorts.RuleStore rules;
    private final TenantAlertPorts.AlertStore alerts;
    private final TenantAlertPorts.NotificationCenterPort notificationCenter;
    private final TenantAlertPorts.BatchFailureNotificationPort batchFailurePort;
    private final ReliableWebhookDelivery webhookDelivery;
    private final WebhookDeliveryPolicy webhookPolicy;
    private final TenantAlertPorts.TenantResponsible responsible;
    private final Clock clock;

    public TenantAlertService(BusinessEventCatalog catalog, TenantAlertPorts.RuleStore rules,
                              TenantAlertPorts.AlertStore alerts,
                              TenantAlertPorts.NotificationCenterPort notificationCenter,
                              TenantAlertPorts.BatchFailureNotificationPort batchFailurePort,
                              ReliableWebhookDelivery webhookDelivery, WebhookDeliveryPolicy webhookPolicy,
                              TenantAlertPorts.TenantResponsible responsible, Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "o catálogo da P1.38 é obrigatório");
        this.rules = Objects.requireNonNull(rules, "a loja de regras é obrigatória");
        this.alerts = Objects.requireNonNull(alerts, "a loja de alertas é obrigatória");
        this.notificationCenter = Objects.requireNonNull(notificationCenter, "a central da P3.36 é obrigatória");
        this.batchFailurePort = Objects.requireNonNull(batchFailurePort, "a porta de falha de lote é obrigatória");
        this.webhookDelivery = Objects.requireNonNull(webhookDelivery, "a entrega de webhook é obrigatória");
        this.webhookPolicy = Objects.requireNonNull(webhookPolicy, "a política de webhook é obrigatória");
        this.responsible = Objects.requireNonNull(responsible, "a porta do responsável é obrigatória");
        this.clock = Objects.requireNonNull(clock, "o relógio é obrigatório");
    }

    // ---------------------------------------------------------- configuração
    public AlertRule configureRule(UUID tenantId, String eventName, int eventVersion,
                                   UUID responsibleUserId, boolean webhookEnabled, UUID createdBy) {
        requireInCatalog(eventName, eventVersion);
        if (rules.existsEnabledDuplicate(tenantId, eventName, eventVersion, responsibleUserId)) {
            throw new AlertRejectedException("duplicate-alert-rule",
                    "Já existe regra ativa deste tenant para o evento " + eventName + " v" + eventVersion
                            + " com o mesmo responsável");
        }
        AlertRule rule = new AlertRule(UUID.randomUUID(), tenantId, eventName, eventVersion,
                responsibleUserId, webhookEnabled, true, createdBy, clock.instant());
        rules.save(rule);
        return rule;
    }

    public AlertRule setRuleEnabled(UUID tenantId, UUID ruleId, boolean enabled) {
        AlertRule rule = rules.findById(tenantId, ruleId).orElseThrow(() ->
                new AlertRejectedException("alert-rule-not-found", "Regra de alerta inexistente neste tenant: " + ruleId));
        AlertRule updated = rule.withEnabled(enabled);
        rules.save(updated);
        return updated;
    }

    // --------------------------------------------------------------- eventos
    public EventResult onBusinessEvent(UUID tenantId, UUID eventId, String eventName, int eventVersion,
                                       Instant occurredAt) {
        Objects.requireNonNull(eventId, "o evento exige o id da mensagem da outbox");
        Objects.requireNonNull(occurredAt, "o evento exige o instante de ocorrência");
        requireInCatalog(eventName, eventVersion);
        List<AlertRule> matching = rules.findEnabled(tenantId, eventName, eventVersion);
        if (matching.isEmpty()) {
            return new EventResult(EventOutcome.IGNORED_NO_RULE, List.of());
        }
        String sourceRef = eventId.toString();
        List<RuleDelivery> deliveries = new java.util.ArrayList<>();
        for (AlertRule rule : matching) {
            Optional<TenantAlert> existing = alerts.findBySource(tenantId, rule.id(), sourceRef);
            if (existing.isPresent()) {
                deliveries.add(new RuleDelivery(rule.id(), existing.get().id(), true, null));
                continue;
            }
            TenantAlert alert = new TenantAlert(UUID.randomUUID(), tenantId, rule.id(),
                    TenantAlert.Kind.CATALOG_EVENT, eventName, eventVersion, sourceRef,
                    rule.responsibleUserId(), titleCodeOf(eventName), bodyCodeOf(eventName), clock.instant());
            // Correção D4: ENTREGA ANTES de gravar. Se a central lançar, o
            // alerta NÃO fica gravado e a reentrega tenta de verdade.
            notificationCenter.deliver(new TenantAlertPorts.NotificationRequest(tenantId,
                    rule.responsibleUserId(), eventName, eventVersion, TenantAlert.Kind.CATALOG_EVENT,
                    alert.titleCode(), alert.bodyCode(), sourceRef));
            ReliableWebhookDelivery.Result webhook = null;
            if (rule.webhookEnabled()) {
                String payload = webhookPayload(alert, occurredAt);
                webhook = webhookDelivery.deliver(tenantId, alert.id(), eventName, payload, webhookPolicy);
            }
            alerts.save(alert);
            deliveries.add(new RuleDelivery(rule.id(), alert.id(), false, webhook));
        }
        return new EventResult(EventOutcome.ALERTED, deliveries);
    }

    // ------------------------------------------------------------ falha lote
    public BatchResult onBatchFailure(BatchFailure failure) {
        String runRef = failure.runId().toString();
        Optional<TenantAlert> existing = alerts.findBatchFailure(failure.tenantId(), runRef);
        if (existing.isPresent()) {
            return new BatchResult(BatchOutcome.DUPLICATE, existing.get().id());
        }
        Optional<UUID> who = responsible.batchResponsibleOf(failure.tenantId());
        if (who.isEmpty()) {
            return new BatchResult(BatchOutcome.NO_RESPONSIBLE, null);
        }
        TenantAlert alert = new TenantAlert(UUID.randomUUID(), failure.tenantId(), null,
                TenantAlert.Kind.BATCH_FAILURE, null, null, runRef, who.get(),
                BATCH_FAILURE_TITLE_CODE, BATCH_FAILURE_BODY_CODE, clock.instant());
        // Correções D1 + D4: a falha de lote sai pela porta TIPADA própria
        // (nunca como NotificationRequest sem evento à central real) e a
        // ENTREGA precede a gravação — falha na entrega não queima o alerta:
        // a repetição da falha tenta notificar de novo.
        batchFailurePort.notify(new TenantAlertPorts.BatchFailureNotice(failure.tenantId(), who.get(),
                runRef, failure.cycleRef(), failure.failedRoutes(), failure.errorCode(),
                BATCH_FAILURE_TITLE_CODE, BATCH_FAILURE_BODY_CODE, clock.instant()));
        alerts.save(alert);
        return new BatchResult(BatchOutcome.NOTIFIED, alert.id());
    }

    public List<TenantAlert> listAlerts(UUID tenantId, int limit) {
        return alerts.listByTenant(tenantId, limit);
    }

    // ---------------------------------------------------------------- apoios
    private void requireInCatalog(String eventName, int eventVersion) {
        if (eventName == null || eventName.isBlank() || eventVersion < 1
                || catalog.find(eventName, eventVersion).isEmpty()) {
            throw AlertRejectedException.eventNotInCatalog(eventName, eventVersion);
        }
    }

    public static String titleCodeOf(String eventName) {
        return "tenant-alert." + eventName;
    }

    public static String bodyCodeOf(String eventName) {
        return "tenant-alert." + eventName + ".body";
    }

    /** Carga do webhook: SÓ identificadores e códigos (P1.38/P2.26 — sem dado pessoal). */
    public static String webhookPayload(TenantAlert alert, Instant occurredAt) {
        return "{\"event_id\":\"" + alert.id() + "\",\"event_name\":\"" + alert.eventName()
                + "\",\"event_version\":" + alert.eventVersion()
                + ",\"tenant_id\":\"" + alert.tenantId() + "\",\"source_ref\":\"" + alert.sourceRef()
                + "\",\"responsible_user_id\":\"" + alert.responsibleUserId()
                + "\",\"occurred_at\":\"" + occurredAt + "\"}";
    }
}
