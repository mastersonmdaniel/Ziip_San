package br.com.ziipsan.platform.central;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coletor da Central Ziip v0 (P1.17, CENTRAL-ZIIP §5).
 *
 * <p>Cadencia de 5 min (o agendamento vive em
 * {@code CentralCollectorScheduler}, no app, e so roda com
 * {@code ziipsan.central.collector.enabled=true} — o processo recomendado
 * e o worker; se a api tambem o ligar, a trava abaixo impede a coleta
 * em duplicidade no cluster). Cada coleta, numa unica transacao:
 * <ol>
 *   <li>tenta {@code pg_try_advisory_xact_lock} com a chave fixa da Central;
 *       sem a trava, devolve SKIPPED sem escrever nada;</li>
 *   <li>antes de atualizar o marcador, verifica o watchdog: se a coleta
 *       anterior tem mais de 15 min, abre (sem duplicar) o alerta da
 *       regra {@code central.collector.stale} — a prova do gap fica
 *       registrada, mesmo que a coleta atual ja esteja saudavel;</li>
 *   <li>garante a particao do dia de `metric_point`, agrega pelas
 *       funcoes `platform_stats.*` (so contagens e idades, nunca dado
 *       pessoal) e insere um ponto por chave e tenant (e o global);</li>
 *   <li>avalia as regras ligadas e nao em soneca: condicao mantida por
 *       {@code consecutive_readings} leituras abre o alerta (o indice
 *       unico parcial impede duplicar); condicao limpa resolve o
 *       alerta aberto;</li>
 *   <li>consolida o dia anterior em `metric_daily` (DELETE + INSERT do
 *       dia, idempotente) e apaga pontos com mais de 90 dias e diarios
 *       com mais de 24 meses;</li>
 *   <li>atualiza o marcador {@code central.collector.last_run_at}.</li>
 * </ol>
 * <p>Limite honesto do watchdog v0: ele detecta o gap na proxima coleta
 * e expoe o stale em toda leitura (aba Seguranca e Visao geral), mas nao
 * dispara sozinho enquanto nenhum processo esta no ar — a sonda externa
 * que alerta com o processo parado e trabalho da P3.15 (CENTRAL-ZIIP §5
 * fala em "sonda externa" para uptime, e o watchdog proprio aqui e o
 * marcador + o alerta de gap).
 */
@Service
public class CentralCollectorService {

    /** Chave fixa da trava de coleta (advisory lock transacional). */
    static final long LOCK_KEY = 8100170017L;

    /** Watchdog: coleta mais velha que isto e stale (CENTRAL-ZIIP §5). */
    static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private static final String WATCHDOG_RULE_KEY = "central.collector.stale";

    public record Report(String outcome, int pointsWritten, int alertsOpened, int alertsResolved,
                         boolean staleGapDetected) {
    }

    public record WatchdogStatus(OffsetDateTime lastRunAt, boolean stale, Long ageSeconds) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CentralCollectorService(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC());
    }

    CentralCollectorService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Stale sem escrever nada: usado pelas abas e pelo watchdog. */
    public WatchdogStatus watchdogStatus() {
        return watchdogStatus(clock.instant());
    }

    WatchdogStatus watchdogStatus(Instant now) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT item_value ->> 'at' AS at, (item_value ->> 'collected')::BOOLEAN AS collected"
                        + " FROM platform.config_item WHERE item_key = 'central.collector.last_run_at'");
        if (rows.isEmpty()) {
            return new WatchdogStatus(null, true, null);
        }
        String raw = (String) rows.get(0).get("at");
        Boolean collected = (Boolean) rows.get(0).get("collected");
        if (raw == null || !Boolean.TRUE.equals(collected)) {
            return new WatchdogStatus(null, true, null);
        }
        OffsetDateTime last = OffsetDateTime.parse(raw);
        long age = Duration.between(last.toInstant(), now).getSeconds();
        return new WatchdogStatus(last, age > STALE_AFTER.getSeconds(), age);
    }

    @Transactional
    public Report collect() {
        return collect(clock.instant());
    }

    @Transactional
    public Report collect(Instant now) {
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) {
            return new Report("SKIPPED_LOCK", 0, 0, 0, false);
        }
        WatchdogStatus before = watchdogStatus(now);
        boolean gap = before.stale() && before.lastRunAt() != null;
        if (gap) {
            openWatchdogAlert();
        }
        LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);
        // A funcao devolve VOID: executar como statement, nao como query.
        jdbc.execute("SELECT platform.ensure_metric_point_partition(DATE '" + todayUtc + "')");
        OffsetDateTime ts = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        int points = 0;
        points += writeTenantCounts(ts);
        points += writeOutboxPoints(ts);
        points += writeOnboardingPoints(ts);
        points += writeAlertPoints(ts);
        int opened = evaluateRules(now);
        int resolved = resolveClearedAlerts(now);
        // O alerta do watchdog representa um gap passado: abre na coleta
        // que detecta o gap e so resolve na coleta seguinte SAUDAVEL (sem
        // gap) — resolve-lo na mesma coleta que o abriu o tornaria
        // invisivel. Sem gap nesta coleta, gaps antigos ja estao superados
        // por um periodo saudavel e o evento fecha.
        if (!gap) {
            resolved += jdbc.update("""
                    UPDATE platform.alert_event SET status = 'RESOLVED', resolved_at = ?
                    WHERE resolved_at IS NULL AND rule_id IN
                        (SELECT id FROM platform.alert_rule WHERE rule_key = ?)
                    """, Timestamp.from(now), WATCHDOG_RULE_KEY);
        }
        consolidateDaily(todayUtc.minusDays(1));
        applyRetention(todayUtc);
        jdbc.update("UPDATE platform.config_item SET item_value = ?::JSONB, updated_at = NOW()"
                        + " WHERE item_key = 'central.collector.last_run_at'",
                "{\"collected\": true, \"at\": \"" + ts + "\"}");
        return new Report("COLLECTED", points, opened, resolved, gap);
    }

    private void openWatchdogAlert() {
        jdbc.update("""
                INSERT INTO platform.alert_event (rule_id, tenant_id, severity, status)
                SELECT id, NULL, severity, 'OPEN' FROM platform.alert_rule WHERE rule_key = ?
                ON CONFLICT (rule_id, (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::UUID)))
                    WHERE resolved_at IS NULL DO NOTHING
                """, WATCHDOG_RULE_KEY);
    }

    private int writeTenantCounts(OffsetDateTime ts) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM platform_stats.tenant_counts()");
        long total = 0;
        long active = 0;
        for (Map<String, Object> row : rows) {
            long count = ((Number) row.get("tenant_count")).longValue();
            total += count;
            if ("ACTIVE".equals(row.get("status"))) {
                active = count;
            }
        }
        insertPoint("platform.tenants.total", null, total, "{}", ts);
        insertPoint("platform.tenants.active", null, active, "{}", ts);
        return 2;
    }

    private int writeOutboxPoints(OffsetDateTime ts) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM platform_stats.tenant_outbox_counts()");
        long pendingTotal = 0;
        long deadTotal = 0;
        long oldestTotal = 0;
        int points = 0;
        for (Map<String, Object> row : rows) {
            UUID tenantId = (UUID) row.get("tenant_id");
            long pending = ((Number) row.get("pending_count")).longValue();
            long dead = ((Number) row.get("dead_count")).longValue();
            long oldest = ((Number) row.get("oldest_pending_age_s")).longValue();
            pendingTotal += pending;
            deadTotal += dead;
            oldestTotal = Math.max(oldestTotal, oldest);
            insertPoint("platform.outbox.pending", tenantId, pending, "{}", ts);
            insertPoint("platform.outbox.dead", tenantId, dead, "{}", ts);
            insertPoint("platform.outbox.oldest_pending_age_s", tenantId, oldest, "{}", ts);
            points += 3;
        }
        insertPoint("platform.outbox.pending", null, pendingTotal, "{}", ts);
        insertPoint("platform.outbox.dead", null, deadTotal, "{}", ts);
        insertPoint("platform.outbox.oldest_pending_age_s", null, oldestTotal, "{}", ts);
        return points + 3;
    }

    private int writeOnboardingPoints(OffsetDateTime ts) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM platform_stats.onboarding_counts()");
        int points = 0;
        for (Map<String, Object> row : rows) {
            insertPoint("platform.onboarding.pending_steps", (UUID) row.get("tenant_id"),
                    ((Number) row.get("pending_steps")).longValue(), "{}", ts);
            points++;
        }
        return points;
    }

    private int writeAlertPoints(OffsetDateTime ts) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM platform_stats.open_alert_counts()");
        long total = 0;
        int points = 0;
        for (Map<String, Object> row : rows) {
            String severity = (String) row.get("severity");
            long count = ((Number) row.get("open_count")).longValue();
            total += count;
            insertPoint("platform.alerts.open", null, count,
                    "{\"severity\": \"" + severity + "\"}", ts);
            points++;
        }
        if (points == 0) {
            insertPoint("platform.alerts.open", null, 0, "{}", ts);
            return 1;
        }
        return points;
    }

    private void insertPoint(String key, UUID tenantId, long value, String labels, OffsetDateTime ts) {
        jdbc.update("INSERT INTO platform.metric_point (metric_key, tenant_id, metric_value, labels, ts)"
                        + " VALUES (?, ?, ?, ?::JSONB, ?)",
                key, tenantId, value, labels, Timestamp.from(ts.toInstant()));
    }

    /** Abre alertas cuja condicao se manteve por `consecutive_readings` leituras. Devolve quantos abriu. */
    private int evaluateRules(Instant now) {
        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT id, metric_key, comparator, threshold, consecutive_readings, severity, scope"
                        + " FROM platform.alert_rule WHERE enabled AND rule_key <> ?"
                        + " AND (snooze_until IS NULL OR snooze_until <= ?)",
                WATCHDOG_RULE_KEY, Timestamp.from(now));
        int opened = 0;
        for (Map<String, Object> rule : rules) {
            UUID ruleId = (UUID) rule.get("id");
            String metricKey = (String) rule.get("metric_key");
            String comparator = (String) rule.get("comparator");
            double threshold = ((Number) rule.get("threshold")).doubleValue();
            int needed = ((Number) rule.get("consecutive_readings")).intValue();
            String severity = (String) rule.get("severity");
            String scope = (String) rule.get("scope");
            if ("GLOBAL".equals(scope)) {
                if (conditionHolds(metricKey, null, comparator, threshold, needed)) {
                    opened += openAlert(ruleId, null, severity);
                }
            } else {
                List<UUID> tenants = jdbc.queryForList(
                        "SELECT DISTINCT tenant_id FROM platform.metric_point"
                                + " WHERE metric_key = ? AND tenant_id IS NOT NULL", UUID.class, metricKey);
                for (UUID tenantId : tenants) {
                    if (conditionHolds(metricKey, tenantId, comparator, threshold, needed)) {
                        opened += openAlert(ruleId, tenantId, severity);
                    }
                }
            }
        }
        return opened;
    }

    private boolean conditionHolds(String metricKey, UUID tenantId, String comparator, double threshold, int needed) {
        List<Double> last;
        if (tenantId == null) {
            last = jdbc.queryForList(
                    "SELECT metric_value FROM platform.metric_point WHERE metric_key = ? AND tenant_id IS NULL"
                            + " ORDER BY ts DESC LIMIT ?", Double.class, metricKey, needed);
        } else {
            last = jdbc.queryForList(
                    "SELECT metric_value FROM platform.metric_point WHERE metric_key = ? AND tenant_id = ?"
                            + " ORDER BY ts DESC LIMIT ?", Double.class, metricKey, tenantId, needed);
        }
        if (last.size() < needed) {
            return false;
        }
        for (Double value : last) {
            if (!compare(value, comparator, threshold)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compare(double value, String comparator, double threshold) {
        return switch (comparator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "=" -> Double.compare(value, threshold) == 0;
            default -> false;
        };
    }

    private int openAlert(UUID ruleId, UUID tenantId, String severity) {
        return jdbc.update("""
                INSERT INTO platform.alert_event (rule_id, tenant_id, severity, status)
                VALUES (?, ?, ?, 'OPEN')
                ON CONFLICT (rule_id, (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::UUID)))
                    WHERE resolved_at IS NULL DO NOTHING
                """, ruleId, tenantId, severity);
    }

    /** Resolve alertas abertos cuja condicao ja nao se mantem na leitura mais recente. */
    private int resolveClearedAlerts(Instant now) {
        List<Map<String, Object>> open = jdbc.queryForList(
                "SELECT e.id, r.metric_key, r.comparator, r.threshold, e.tenant_id"
                        + " FROM platform.alert_event e JOIN platform.alert_rule r ON r.id = e.rule_id"
                        + " WHERE e.resolved_at IS NULL AND r.rule_key <> ?",
                WATCHDOG_RULE_KEY);
        int resolved = 0;
        for (Map<String, Object> row : open) {
            UUID eventId = (UUID) row.get("id");
            String metricKey = (String) row.get("metric_key");
            String comparator = (String) row.get("comparator");
            double threshold = ((Number) row.get("threshold")).doubleValue();
            UUID tenantId = (UUID) row.get("tenant_id");
            // Lista, nao queryForObject: regra sem ponto ainda (metrica
            // sem fonte) nao pode derrubar a coleta inteira com excecao
            // de resultado vazio — sem leitura, o alerta fica aberto.
            List<Double> latestRows;
            if (tenantId == null) {
                latestRows = jdbc.queryForList(
                        "SELECT metric_value FROM platform.metric_point WHERE metric_key = ? AND tenant_id IS NULL"
                                + " ORDER BY ts DESC LIMIT 1", Double.class, metricKey);
            } else {
                latestRows = jdbc.queryForList(
                        "SELECT metric_value FROM platform.metric_point WHERE metric_key = ? AND tenant_id = ?"
                                + " ORDER BY ts DESC LIMIT 1", Double.class, metricKey, tenantId);
            }
            Double latest = latestRows.isEmpty() ? null : latestRows.get(0);
            if (latest != null && !compare(latest, comparator, threshold)) {
                resolved += jdbc.update("UPDATE platform.alert_event SET status = 'RESOLVED', resolved_at = ?"
                        + " WHERE id = ? AND resolved_at IS NULL", Timestamp.from(now), eventId);
            }
        }
        return resolved;
    }

    private void consolidateDaily(LocalDate day) {
        jdbc.update("DELETE FROM platform.metric_daily WHERE day = ?", Date.valueOf(day));
        jdbc.update("""
                INSERT INTO platform.metric_daily (metric_key, tenant_id, day, avg_value, min_value, max_value, point_count)
                SELECT metric_key, tenant_id, (ts AT TIME ZONE 'UTC')::DATE,
                       AVG(metric_value), MIN(metric_value), MAX(metric_value), COUNT(*)
                FROM platform.metric_point
                WHERE (ts AT TIME ZONE 'UTC')::DATE = ?
                GROUP BY metric_key, tenant_id, (ts AT TIME ZONE 'UTC')::DATE
                """, Date.valueOf(day));
        // Retencao do diario: alem de 24 meses.
        jdbc.update("DELETE FROM platform.metric_daily WHERE day < ?",
                Date.valueOf(day.minusMonths(24)));
    }

    private void applyRetention(LocalDate todayUtc) {
        jdbc.update("DELETE FROM platform.metric_point WHERE ts < ?",
                Timestamp.from(todayUtc.minusDays(90).atStartOfDay(ZoneOffset.UTC).toInstant()));
    }
}
