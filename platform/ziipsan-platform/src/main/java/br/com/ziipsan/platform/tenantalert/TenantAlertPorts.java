package br.com.ziipsan.platform.tenantalert;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ED-07: as portas da entrega. A ED-07 COMPÕE o que as donas já entregam —
 * não cria motor paralelo (precedente da P3.39):
 *
 * <ul>
 *   <li>catálogo de eventos: a porta {@code BusinessEventCatalog} REAL da
 *       P3.36/P1.38, reusada sem cópia;</li>
 *   <li>central de avisos: {@link NotificationCenterPort} — a ligação REAL à
 *       {@code NotificationCenterService} da P3.36 é o
 *       {@link P336NotificationCenterAdapter} desta entrega (correção D1
 *       pós-reprovação): converte o pedido em {@code OutboxMessage} da P3.36
 *       e chama {@code ingest} de verdade (a ingestão recusa evento fora do
 *       catálogo e deduplica pela mensagem de origem);</li>
 *   <li>falha de lote: {@link BatchFailureNotificationPort} — porta TIPADA
 *       própria, porque a falha de lote NÃO é evento do catálogo da P1.38 e
 *       a API real da P3.36 só ingere evento do catálogo (ver o adaptador e
 *       o RELATORIO-ED-07, correção D1);</li>
 *   <li>webhook de saída: {@link WebhookEndpoints} + {@link WebhookTransport}
 *       — a fiação liga ao cadastro/transporte da P2.26
 *       ({@code webhook_endpoint}/{@code webhook_delivery}); a ED-07 assina
 *       ({@link WebhookSignature}) e aplica a política de repetição
 *       ({@link WebhookDeliveryPolicy});</li>
 *   <li>responsável por falha de lote: {@link TenantResponsible} — a fiação
 *       resolve o responsável do tenant (administrador/responsável
 *       operacional configurado); sem responsável resolvido, a falha NÃO é
 *       entregue a ninguém inventado: o serviço devolve o desfecho
 *       {@code NO_RESPONSIBLE} declarado;</li>
 *   <li>painel: {@link BatchStatsSource} lê os {@code job_runs} da P2.1 e
 *       {@link WebhookStatsSource} lê as entregas da P2.26; fonte ausente =
 *       seção indisponível no painel, nunca zero fingido.</li>
 * </ul>
 *
 * As implementações em memória servem ao ambiente local e aos testes; a
 * persistência real usa as tabelas do SQL pendente desta entrega
 * ({@code tenant_alert_rules}, {@code tenant_alerts},
 * {@code tenant_alert_webhook_states}) na integração.
 */
public final class TenantAlertPorts {

    private TenantAlertPorts() {
    }

    // ---------------------------------------------------------------- regras
    public interface RuleStore {
        void save(AlertRule rule);

        Optional<AlertRule> findById(UUID tenantId, UUID ruleId);

        /** Regras ATIVAS do tenant para (evento, versão): é a "escolha do tenant" do aceite. */
        List<AlertRule> findEnabled(UUID tenantId, String eventName, int eventVersion);

        boolean existsEnabledDuplicate(UUID tenantId, String eventName, int eventVersion, UUID responsibleUserId);

        List<AlertRule> listByTenant(UUID tenantId);
    }

    public static final class InMemoryRuleStore implements RuleStore {
        private final Map<UUID, AlertRule> byId = new LinkedHashMap<>();

        @Override public synchronized void save(AlertRule rule) { byId.put(rule.id(), rule); }

        @Override public synchronized Optional<AlertRule> findById(UUID tenantId, UUID ruleId) {
            AlertRule r = byId.get(ruleId);
            return r != null && r.tenantId().equals(tenantId) ? Optional.of(r) : Optional.empty();
        }

        @Override public synchronized List<AlertRule> findEnabled(UUID tenantId, String eventName, int eventVersion) {
            return byId.values().stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.enabled()
                            && r.eventName().equals(eventName) && r.eventVersion() == eventVersion)
                    .toList();
        }

        @Override public synchronized boolean existsEnabledDuplicate(UUID tenantId, String eventName,
                                                                     int eventVersion, UUID responsibleUserId) {
            return byId.values().stream().anyMatch(r -> r.tenantId().equals(tenantId) && r.enabled()
                    && r.eventName().equals(eventName) && r.eventVersion() == eventVersion
                    && r.responsibleUserId().equals(responsibleUserId));
        }

        @Override public synchronized List<AlertRule> listByTenant(UUID tenantId) {
            return byId.values().stream().filter(r -> r.tenantId().equals(tenantId)).toList();
        }
    }

    // --------------------------------------------------------------- alertas
    public interface AlertStore {
        void save(TenantAlert alert);

        /** Idempotência da entrega pelo menos uma vez da outbox (P1.9): mesma origem + mesma regra não duplica. */
        Optional<TenantAlert> findBySource(UUID tenantId, UUID ruleId, String sourceRef);

        Optional<TenantAlert> findBatchFailure(UUID tenantId, String runRef);

        List<TenantAlert> listByTenant(UUID tenantId, int limit);

        /** Contagem do painel no período [from, to): inclui {@code from}, exclui {@code to} (correção D2). */
        long countByTenantBetween(UUID tenantId, Instant from, Instant to);
    }

    public static final class InMemoryAlertStore implements AlertStore {
        private final Map<UUID, TenantAlert> byId = new LinkedHashMap<>();

        @Override public synchronized void save(TenantAlert alert) { byId.put(alert.id(), alert); }

        @Override public synchronized Optional<TenantAlert> findBySource(UUID tenantId, UUID ruleId, String sourceRef) {
            return byId.values().stream().filter(a -> a.tenantId().equals(tenantId)
                    && ruleId.equals(a.ruleId()) && a.sourceRef().equals(sourceRef)).findFirst();
        }

        @Override public synchronized Optional<TenantAlert> findBatchFailure(UUID tenantId, String runRef) {
            return byId.values().stream().filter(a -> a.tenantId().equals(tenantId)
                    && a.kind() == TenantAlert.Kind.BATCH_FAILURE
                    && a.sourceRef().equals(runRef)).findFirst();
        }

        @Override public synchronized List<TenantAlert> listByTenant(UUID tenantId, int limit) {
            return byId.values().stream().filter(a -> a.tenantId().equals(tenantId))
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).limit(limit).toList();
        }

        @Override public synchronized long countByTenantBetween(UUID tenantId, Instant from, Instant to) {
            return byId.values().stream().filter(a -> a.tenantId().equals(tenantId)
                    && !a.createdAt().isBefore(from) && a.createdAt().isBefore(to)).count();
        }
    }

    // ------------------------------------------------- central de avisos P3.36
    /**
     * Pedido de entrega na central: códigos de mensagem, nunca texto livre com
     * dado pessoal. Só pedidos {@code CATALOG_EVENT} (nome + versão de evento
     * do catálogo) são ingeríveis pela central real da P3.36 — ver
     * {@link P336NotificationCenterAdapter}; a falha de lote NÃO viaja por
     * este pedido (correção D1): ela tem a porta tipada própria abaixo.
     */
    public record NotificationRequest(UUID tenantId, UUID recipientUserId, String eventName, Integer eventVersion,
                                      TenantAlert.Kind kind, String titleCode, String bodyCode, String sourceRef) {
    }

    public interface NotificationCenterPort {
        void deliver(NotificationRequest request);
    }

    // ------------------------------------------------------- falha de lote
    /**
     * Aviso TIPADO de falha de lote ao responsável do tenant (correção D1):
     * carrega só identificadores e códigos da execução da P2.1 — NENHUM nome
     * de evento do catálogo da P1.38, porque a falha de lote não é um evento
     * declarado nele. É o canal interno que o serviço usa; a ligação dele à
     * central real da P3.36, quando a integração declarar o evento, é o
     * {@code P336NotificationCenterAdapter.deliverBatchFailureWithDeclaredEvent}.
     */
    public record BatchFailureNotice(UUID tenantId, UUID recipientUserId, String runRef, String cycleRef,
                                     List<String> failedRoutes, String errorCode,
                                     String titleCode, String bodyCode, Instant occurredAt) {
        public BatchFailureNotice {
            java.util.Objects.requireNonNull(tenantId, "o aviso de falha de lote exige o tenant");
            java.util.Objects.requireNonNull(recipientUserId, "o aviso de falha de lote exige o responsável");
            if (runRef == null || runRef.isBlank()) {
                throw new IllegalArgumentException("o aviso de falha de lote exige a execução (job_runs)");
            }
            if (titleCode == null || titleCode.isBlank() || bodyCode == null || bodyCode.isBlank()) {
                throw new IllegalArgumentException("o aviso de falha de lote exige os códigos de título e corpo");
            }
            java.util.Objects.requireNonNull(occurredAt, "o aviso de falha de lote exige o instante");
            failedRoutes = failedRoutes == null ? List.of() : List.copyOf(failedRoutes);
        }
    }

    public interface BatchFailureNotificationPort {
        void notify(BatchFailureNotice notice);
    }

    // ------------------------------------------------------- webhook P2.26
    /** Endpoint de saída do tenant para um evento, como a P2.26 o cadastra. O segredo vem do cofre da fiação. */
    public record WebhookEndpoint(UUID tenantId, String url, String secret, boolean enabled) {
    }

    public interface WebhookEndpoints {
        /** Vazio = tenant não configurou webhook para este evento (entrega só na central). */
        Optional<WebhookEndpoint> endpointFor(UUID tenantId, String eventName);
    }

    /** Requisição já assinada: a fiação HTTP da P2.26 só transporta; quem assina é a ED-07. */
    public record SignedWebhookRequest(UUID tenantId, UUID eventId, String url, String payloadJson,
                                       long timestampEpochSeconds, String signature) {
    }

    public record TransportResult(boolean delivered, Integer httpStatus, String error) {
        public static TransportResult ok(int status) { return new TransportResult(true, status, null); }
        public static TransportResult failed(String error) { return new TransportResult(false, null, error); }
    }

    public interface WebhookTransport {
        TransportResult send(SignedWebhookRequest request);
    }

    /** Espera entre repetições: a fiação dorme de verdade; o teste registra sem dormir. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration);
    }

    /** Estado de confiabilidade por (tenant, evento): falhas consecutivas e desativação automática da P2.26. */
    public record WebhookEndpointState(UUID tenantId, String eventName, int consecutiveFailures, boolean disabled) {
        public WebhookEndpointState {
            if (consecutiveFailures < 0) {
                throw new IllegalArgumentException("falhas consecutivas não podem ser negativas");
            }
        }
        public static WebhookEndpointState initial(UUID tenantId, String eventName) {
            return new WebhookEndpointState(tenantId, eventName, 0, false);
        }
    }

    public interface WebhookStateStore {
        WebhookEndpointState stateOf(UUID tenantId, String eventName);

        void save(WebhookEndpointState state);
    }

    public static final class InMemoryWebhookStateStore implements WebhookStateStore {
        private final Map<String, WebhookEndpointState> byKey = new LinkedHashMap<>();

        private static String key(UUID tenantId, String eventName) { return tenantId + "|" + eventName; }

        @Override public synchronized WebhookEndpointState stateOf(UUID tenantId, String eventName) {
            return byKey.getOrDefault(key(tenantId, eventName), WebhookEndpointState.initial(tenantId, eventName));
        }

        @Override public synchronized void save(WebhookEndpointState state) {
            byKey.put(key(state.tenantId(), state.eventName()), state);
        }
    }

    // ------------------------------------------------- responsável do tenant
    public interface TenantResponsible {
        /** Responsável do tenant para falhas de lote; vazio = ninguém configurado (desfecho declarado, sem invenção). */
        Optional<UUID> batchResponsibleOf(UUID tenantId);
    }

    // ------------------------------------------------------------ painel
    /** Contadores de lote do período, lidos dos {@code job_runs} da P2.1 pela fiação. */
    public record BatchStats(long runsTotal, long runsDone, long runsFailed, long runsInterrupted,
                             Duration averageDuration) {
    }

    public interface BatchStatsSource {
        Optional<BatchStats> statsOf(UUID tenantId, Instant from, Instant to);
    }

    /** Contadores de entrega de webhook do período, lidos das entregas da P2.26 pela fiação. */
    public record WebhookStats(long deliveriesTotal, long deliveriesSucceeded, long deliveriesFailed,
                               int endpointsDisabled) {
    }

    public interface WebhookStatsSource {
        Optional<WebhookStats> statsOf(UUID tenantId, Instant from, Instant to);
    }
}
