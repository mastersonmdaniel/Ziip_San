package br.com.ziipsan.platform.central;

import br.com.ziipsan.core.api.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leituras e acoes da Central Ziip v0 (P1.17), sobre o console v0 (P1.6).
 *
 * <p>Esta classe NAO reconstroi o console: perfis, planos, jornada,
 * suspensao e eliminacao continuam em {@code PlatformConsoleService} e
 * nas rotas {@code /api/v1/platform/tenants/{id}/console}. Aqui vivem
 * so as abas da Central (Visao geral, Plataforma, Seguranca, Alertas),
 * a API de metricas v1 (CENTRAL-ZIIP §8) e a gestao de regras de alerta.
 *
 * <p>Privacidade: nenhuma leitura daqui toca tabela de tenant. Todas as
 * fontes sao `platform.*`, `public.tenant_catalog` (so status e versao
 * de schema, nunca nome/CNPJ/e-mail) e `public.outbox` via
 * `platform_stats.*` (so contagens e idades). O `tenant_ref` das
 * respostas e o UUID do catalogo, nunca nome ou documento.
 *
 * <p>Health Score (CENTRAL-ZIIP §3): pesos do plano — disponibilidade e
 * erros 5xx 25, ciclo e lote 25, fiscal 20, arrecadacao e Pix 15,
 * conformidade 15. Na v0 so dois fatores tem fonte em banco (outbox e
 * alertas); os demais entram na lista `unmeasured` e o peso e
 * redistribuido entre os medidos, como o proprio documento preve
 * ("fator sem fonte ainda entra com peso redistribuido e aviso 'nao
 * medido'"). Nenhum fator sem fonte recebe ponto inventado.
 */
@Service
public class CentralService {

    public record MetricDefinition(String key, String unit, String type, String description, int version,
                                   String owner) {
    }

    public record MetricPoint(String key, UUID tenantRef, double value, String unit,
                              Map<String, Object> labels, OffsetDateTime collectedAt) {
    }

    public record AlertView(UUID id, String ruleKey, UUID tenantRef, String severity, String status,
                            OffsetDateTime openedAt, UUID acknowledgedBy, OffsetDateTime acknowledgedAt,
                            OffsetDateTime resolvedAt) {
    }

    public record RuleView(UUID id, String ruleKey, String metricKey, String comparator, double threshold,
                           int consecutiveReadings, String severity, String scope, boolean enabled,
                           OffsetDateTime snoozeUntil, String owner) {
    }

    public record RuleInput(String ruleKey, String metricKey, String comparator, double threshold,
                            int consecutiveReadings, String severity, String scope, String owner) {
    }

    public record HealthFactor(String key, int weight, int points, String reason, boolean measured) {
    }

    public record HealthScore(UUID tenantRef, int score, String status, List<HealthFactor> factors,
                              List<String> unmeasured) {
    }

    public record FocusItem(String kind, String detail) {
    }

    public record Overview(List<HealthScore> healthScores, List<FocusItem> todayFocus,
                           CentralCollectorService.WatchdogStatus watchdog,
                           List<Map<String, Object>> trend7d) {
    }

    public record PlatformTab(List<Map<String, Object>> tenantsByStatus,
                              List<Map<String, Object>> lifecycleByStatus,
                              List<Map<String, Object>> schemaVersions,
                              List<Map<String, Object>> outboxByTenant,
                              List<Map<String, Object>> onboardingByTenant) {
    }

    public record SecurityTab(List<Map<String, Object>> auditActions24h, long auditEvents24h,
                              List<Map<String, Object>> openAlertsBySeverity, long suspendedTenants,
                              CentralCollectorService.WatchdogStatus watchdog) {
    }

    public record AlertsTab(List<AlertView> open, List<AlertView> recentResolved, List<RuleView> rules) {
    }

    private static final Set<String> COMPARATORS = Set.of(">", ">=", "<", "<=", "=");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");
    private static final Set<String> SCOPES = Set.of("GLOBAL", "TENANT");
    private static final List<String> UNMEASURED =
            List.of("availability", "billing_cycle", "fiscal", "collection_pix", "compliance");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CentralCollectorService collector;

    @Autowired
    public CentralService(DataSource dataSource, CentralCollectorService collector) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC(), collector);
    }

    CentralService(JdbcTemplate jdbc, Clock clock, CentralCollectorService collector) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.collector = collector;
    }

    // ------------------------------------------------------------------
    // API de metricas v1 (CENTRAL-ZIIP §8)
    // ------------------------------------------------------------------

    public List<MetricDefinition> catalog() {
        return jdbc.query("SELECT metric_key, unit, metric_type, description, version, owner"
                        + " FROM platform.metric_definition ORDER BY metric_key",
                (rs, i) -> new MetricDefinition(rs.getString("metric_key"), rs.getString("unit"),
                        rs.getString("metric_type"), rs.getString("description"), rs.getInt("version"),
                        rs.getString("owner")));
    }

    private String unitOf(String key) {
        List<String> units = jdbc.queryForList(
                "SELECT unit FROM platform.metric_definition WHERE metric_key = ?", String.class, key);
        if (units.isEmpty()) {
            throw new ApiException.NotFound("A metrica nao existe no catalogo.");
        }
        return units.get(0);
    }

    public List<MetricPoint> snapshot() {
        return jdbc.query("""
                SELECT DISTINCT ON (p.metric_key, p.tenant_id) p.metric_key, p.tenant_id, p.metric_value,
                       p.labels, p.ts, d.unit
                FROM platform.metric_point p JOIN platform.metric_definition d ON d.metric_key = p.metric_key
                ORDER BY p.metric_key, p.tenant_id, p.ts DESC
                """, (rs, i) -> toPoint(rs.getString("metric_key"), (UUID) rs.getObject("tenant_id"),
                rs.getDouble("metric_value"), rs.getString("unit"), rs.getString("labels"),
                rs.getTimestamp("ts")));
    }

    public List<MetricPoint> series(String key, UUID tenantId, OffsetDateTime from, OffsetDateTime to, String step) {
        String unit = unitOf(key);
        if ("1d".equals(step)) {
            LocalDate fromDay = from.toLocalDate();
            LocalDate toDay = to.toLocalDate();
            if (tenantId == null) {
                return jdbc.query("SELECT metric_key, tenant_id, avg_value, day FROM platform.metric_daily"
                                + " WHERE metric_key = ? AND tenant_id IS NULL AND day BETWEEN ? AND ? ORDER BY day",
                        (rs, i) -> new MetricPoint(key, null, rs.getDouble("avg_value"), unit, Map.of(),
                                rs.getDate("day").toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)),
                        key, java.sql.Date.valueOf(fromDay), java.sql.Date.valueOf(toDay));
            }
            return jdbc.query("SELECT metric_key, tenant_id, avg_value, day FROM platform.metric_daily"
                            + " WHERE metric_key = ? AND tenant_id = ? AND day BETWEEN ? AND ? ORDER BY day",
                    (rs, i) -> new MetricPoint(key, tenantId, rs.getDouble("avg_value"), unit, Map.of(),
                            rs.getDate("day").toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)),
                    key, tenantId, java.sql.Date.valueOf(fromDay), java.sql.Date.valueOf(toDay));
        }
        if (!"5m".equals(step)) {
            throw validation("O passo precisa ser 5m ou 1d.");
        }
        if (tenantId == null) {
            return jdbc.query("SELECT metric_key, tenant_id, metric_value, labels, ts FROM platform.metric_point"
                            + " WHERE metric_key = ? AND tenant_id IS NULL AND ts BETWEEN ? AND ? ORDER BY ts",
                    (rs, i) -> toPoint(key, null, rs.getDouble("metric_value"), unit, rs.getString("labels"),
                            rs.getTimestamp("ts")),
                    key, Timestamp.from(from.toInstant()), Timestamp.from(to.toInstant()));
        }
        return jdbc.query("SELECT metric_key, tenant_id, metric_value, labels, ts FROM platform.metric_point"
                        + " WHERE metric_key = ? AND tenant_id = ? AND ts BETWEEN ? AND ? ORDER BY ts",
                (rs, i) -> toPoint(key, tenantId, rs.getDouble("metric_value"), unit, rs.getString("labels"),
                        rs.getTimestamp("ts")),
                key, tenantId, Timestamp.from(from.toInstant()), Timestamp.from(to.toInstant()));
    }

    private static MetricPoint toPoint(String key, UUID tenantId, double value, String unit, String labelsJson,
                                       java.sql.Timestamp ts) {
        Map<String, Object> labels = Map.of();
        if (labelsJson != null && !labelsJson.isBlank() && !"{}".equals(labelsJson)) {
            try {
                labels = br.com.ziipsan.core.json.Json.readMap(labelsJson);
            } catch (Exception ignored) {
                labels = Map.of();
            }
        }
        return new MetricPoint(key, tenantId, value, unit, labels,
                ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC));
    }

    public List<AlertView> alerts(String state) {
        String where = switch (state == null ? "open" : state) {
            case "open" -> " WHERE e.resolved_at IS NULL";
            case "resolved" -> " WHERE e.resolved_at IS NOT NULL";
            case "all" -> "";
            default -> throw validation("O estado precisa ser open, resolved ou all.");
        };
        return jdbc.query("SELECT e.id, r.rule_key, e.tenant_id, e.severity, e.status, e.opened_at,"
                        + " e.acknowledged_by, e.acknowledged_at, e.resolved_at"
                        + " FROM platform.alert_event e JOIN platform.alert_rule r ON r.id = e.rule_id"
                        + where + " ORDER BY e.opened_at DESC LIMIT 200",
                (rs, i) -> new AlertView((UUID) rs.getObject("id"), rs.getString("rule_key"),
                        (UUID) rs.getObject("tenant_id"), rs.getString("severity"), rs.getString("status"),
                        toOffset(rs.getTimestamp("opened_at")), (UUID) rs.getObject("acknowledged_by"),
                        toOffset(rs.getTimestamp("acknowledged_at")), toOffset(rs.getTimestamp("resolved_at"))));
    }

    private static OffsetDateTime toOffset(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------
    // Health Score
    // ------------------------------------------------------------------

    public List<HealthScore> healthScores() {
        List<Map<String, Object>> outbox = jdbc.queryForList("SELECT * FROM platform_stats.tenant_outbox_counts()");
        Map<UUID, long[]> byTenant = new LinkedHashMap<>();
        long globalPending = 0;
        long globalDead = 0;
        for (Map<String, Object> row : outbox) {
            UUID tenantId = (UUID) row.get("tenant_id");
            long pending = ((Number) row.get("pending_count")).longValue();
            long dead = ((Number) row.get("dead_count")).longValue();
            byTenant.put(tenantId, new long[]{pending, dead});
            globalPending += pending;
            globalDead += dead;
        }
        Map<String, Long> alertsBySeverity = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM platform_stats.open_alert_counts()")) {
            alertsBySeverity.put((String) row.get("severity"), ((Number) row.get("open_count")).longValue());
        }
        long critical = alertsBySeverity.getOrDefault("CRITICAL", 0L);
        long warning = alertsBySeverity.getOrDefault("WARNING", 0L);
        List<HealthScore> scores = new ArrayList<>();
        for (Map.Entry<UUID, long[]> entry : byTenant.entrySet()) {
            scores.add(scoreFor(entry.getKey(), entry.getValue()[0], entry.getValue()[1], critical, warning));
        }
        // Global: outbox agregado; alertas sao globais por natureza (o
        // evento carrega tenant, mas o score global usa o total aberto).
        scores.add(scoreFor(null, globalPending, globalDead, critical, warning));
        return scores;
    }

    private HealthScore scoreFor(UUID tenantRef, long pending, long dead, long critical, long warning) {
        int outboxPoints;
        String outboxReason;
        if (dead > 0) {
            outboxPoints = 0;
            outboxReason = "Outbox com mensagens DEAD";
        } else if (pending > 100) {
            outboxPoints = 50;
            outboxReason = "Outbox com muitas mensagens pendentes";
        } else {
            outboxPoints = 100;
            outboxReason = "Outbox sem mensagens DEAD";
        }
        int alertPoints;
        String alertReason;
        if (critical > 0) {
            alertPoints = 0;
            alertReason = "Alertas criticos abertos";
        } else if (warning > 0) {
            alertPoints = 60;
            alertReason = "Alertas de aviso abertos";
        } else {
            alertPoints = 100;
            alertReason = "Nenhum alerta aberto";
        }
        // Pesos redistribuidos entre os medidos (25 + 25 do plano).
        int score = (outboxPoints * 25 + alertPoints * 25) / 50;
        String status = score >= 85 ? "HEALTHY" : score >= 65 ? "ATTENTION" : "CRITICAL";
        return new HealthScore(tenantRef, score, status,
                List.of(new HealthFactor("outbox", 25, outboxPoints, outboxReason, true),
                        new HealthFactor("alerts", 25, alertPoints, alertReason, true)),
                UNMEASURED);
    }

    // ------------------------------------------------------------------
    // Abas
    // ------------------------------------------------------------------

    public Overview overview() {
        List<HealthScore> scores = healthScores();
        CentralCollectorService.WatchdogStatus watchdog = collector.watchdogStatus();
        List<FocusItem> focus = new ArrayList<>();
        if (watchdog.stale()) {
            focus.add(new FocusItem("collector_stale",
                    watchdog.lastRunAt() == null ? "O coletor nunca coletou"
                            : "O coletor nao coleta ha mais de 15 minutos"));
        }
        Long dead = jdbc.queryForObject(
                "SELECT COALESCE(SUM(dead_count), 0) FROM platform_stats.tenant_outbox_counts()", Long.class);
        if (dead != null && dead > 0) {
            focus.add(new FocusItem("outbox_dead", "Outbox com " + dead + " mensagem(ns) DEAD"));
        }
        Long critical = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.alert_event WHERE resolved_at IS NULL AND severity = 'CRITICAL'",
                Long.class);
        if (critical != null && critical > 0) {
            focus.add(new FocusItem("critical_alerts", critical + " alerta(s) critico(s) aberto(s)"));
        }
        if (focus.isEmpty()) {
            focus.add(new FocusItem("ok", "Nenhum problema aberto na Central"));
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<Map<String, Object>> trend = jdbc.queryForList(
                "SELECT metric_key, day, avg_value, point_count FROM platform.metric_daily"
                        + " WHERE tenant_id IS NULL AND day BETWEEN ? AND ?"
                        + " AND metric_key IN ('platform.outbox.dead', 'platform.tenants.total')"
                        + " ORDER BY day, metric_key",
                java.sql.Date.valueOf(today.minusDays(7)), java.sql.Date.valueOf(today));
        return new Overview(scores, focus.subList(0, Math.min(3, focus.size())), watchdog, trend);
    }

    public PlatformTab platformTab() {
        return new PlatformTab(
                jdbc.queryForList("SELECT * FROM platform_stats.tenant_counts()"),
                jdbc.queryForList("SELECT status, COUNT(*) AS tenant_count FROM platform.tenant_lifecycle GROUP BY status"),
                jdbc.queryForList("SELECT tenant_schema_version, COUNT(*) AS tenant_count"
                        + " FROM public.tenant_catalog GROUP BY tenant_schema_version"),
                jdbc.queryForList("SELECT * FROM platform_stats.tenant_outbox_counts()"),
                jdbc.queryForList("SELECT * FROM platform_stats.onboarding_counts()"));
    }

    public SecurityTab securityTab() {
        OffsetDateTime since = OffsetDateTime.ofInstant(clock.instant().minusSeconds(86400), ZoneOffset.UTC);
        List<Map<String, Object>> actions = jdbc.queryForList(
                "SELECT * FROM platform_stats.audit_action_counts(?)", Timestamp.from(since.toInstant()));
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.audit_event WHERE created_at >= ?",
                Long.class, Timestamp.from(since.toInstant()));
        long suspended = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform.tenant_lifecycle WHERE status IN ('SUSPENDED', 'SUSPENSION_NOTICE')",
                Long.class);
        return new SecurityTab(actions, total == null ? 0 : total,
                jdbc.queryForList("SELECT * FROM platform_stats.open_alert_counts()"),
                suspended, collector.watchdogStatus());
    }

    public AlertsTab alertsTab() {
        return new AlertsTab(alerts("open"), alerts("resolved"), listRules());
    }

    // ------------------------------------------------------------------
    // Acoes de alerta e regras
    // ------------------------------------------------------------------

    @Transactional
    public AlertView acknowledgeAlert(UUID alertId, UUID actorId, String actorRole) {
        int updated = jdbc.update("UPDATE platform.alert_event SET status = 'ACKNOWLEDGED',"
                        + " acknowledged_by = ?, acknowledged_at = NOW()"
                        + " WHERE id = ? AND status = 'OPEN' AND resolved_at IS NULL",
                actorId, alertId);
        if (updated == 0) {
            throw new ApiException.NotFound("O alerta nao esta aberto.");
        }
        audit(actorId, actorRole, "central.alert.acknowledged", alertId);
        List<AlertView> found = jdbc.query("SELECT e.id, r.rule_key, e.tenant_id, e.severity, e.status, e.opened_at,"
                        + " e.acknowledged_by, e.acknowledged_at, e.resolved_at"
                        + " FROM platform.alert_event e JOIN platform.alert_rule r ON r.id = e.rule_id"
                        + " WHERE e.id = ?",
                (rs, i) -> new AlertView((UUID) rs.getObject("id"), rs.getString("rule_key"),
                        (UUID) rs.getObject("tenant_id"), rs.getString("severity"), rs.getString("status"),
                        toOffset(rs.getTimestamp("opened_at")), (UUID) rs.getObject("acknowledged_by"),
                        toOffset(rs.getTimestamp("acknowledged_at")), toOffset(rs.getTimestamp("resolved_at"))),
                alertId);
        return found.stream().findFirst()
                .orElseThrow(() -> new ApiException.NotFound("O alerta nao existe."));
    }

    @Transactional
    public RuleView snoozeRule(String ruleKey, OffsetDateTime until, UUID actorId, String actorRole) {
        if (until == null || !until.isAfter(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw validation("A soneca precisa terminar no futuro.");
        }
        int updated = jdbc.update("UPDATE platform.alert_rule SET snooze_until = ?, updated_at = NOW()"
                + " WHERE rule_key = ?", Timestamp.from(until.toInstant()), ruleKey);
        if (updated == 0) {
            throw new ApiException.NotFound("A regra nao existe.");
        }
        audit(actorId, actorRole, "central.rule.snoozed", null);
        return listRules().stream().filter(r -> r.ruleKey().equals(ruleKey)).findFirst()
                .orElseThrow(() -> new ApiException.NotFound("A regra nao existe."));
    }

    public List<RuleView> listRules() {
        return jdbc.query("SELECT id, rule_key, metric_key, comparator, threshold, consecutive_readings,"
                        + " severity, scope, enabled, snooze_until, owner FROM platform.alert_rule ORDER BY rule_key",
                (rs, i) -> new RuleView((UUID) rs.getObject("id"), rs.getString("rule_key"),
                        rs.getString("metric_key"), rs.getString("comparator"), rs.getDouble("threshold"),
                        rs.getInt("consecutive_readings"), rs.getString("severity"), rs.getString("scope"),
                        rs.getBoolean("enabled"), toOffset(rs.getTimestamp("snooze_until")),
                        rs.getString("owner")));
    }

    @Transactional
    public RuleView createRule(RuleInput in, UUID actorId, String actorRole) {
        if (in == null || in.ruleKey() == null || in.ruleKey().isBlank()
                || in.metricKey() == null || in.metricKey().isBlank()
                || in.owner() == null || in.owner().isBlank()) {
            throw validation("A chave da regra, a metrica e o dono sao obrigatorios.");
        }
        if (!COMPARATORS.contains(in.comparator())) {
            throw validation("O comparador precisa ser >, >=, <, <= ou =.");
        }
        if (!SEVERITIES.contains(in.severity())) {
            throw validation("A severidade precisa ser INFO, WARNING ou CRITICAL.");
        }
        if (!SCOPES.contains(in.scope())) {
            throw validation("O escopo precisa ser GLOBAL ou TENANT.");
        }
        if (in.consecutiveReadings() < 1) {
            throw validation("As leituras consecutivas precisam ser no minimo 1.");
        }
        unitOf(in.metricKey());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.alert_rule (id, rule_key, metric_key, comparator, threshold,"
                        + " consecutive_readings, severity, scope, owner) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, in.ruleKey(), in.metricKey(), in.comparator(), in.threshold(),
                in.consecutiveReadings(), in.severity(), in.scope(), in.owner());
        audit(actorId, actorRole, "central.rule.created", null);
        return listRules().stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("A regra criada nao foi encontrada."));
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }

    private void audit(UUID actorId, String actorRole, String action, UUID alertId) {
        jdbc.update("INSERT INTO platform.audit_event (tenant_id, actor_id, actor_role, action, details)"
                        + " VALUES (NULL, ?, ?, ?, ?::JSONB)",
                actorId, actorRole == null ? "PLATFORM" : actorRole, action,
                alertId == null ? "{}" : "{\"alert_id\": \"" + alertId + "\"}");
    }
}
