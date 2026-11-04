package br.com.ziipsan.platform.contract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Avaliacao dos alertas do item 7 da ADR-0019 (P2.12): contrato a
 * vencer em 180, 90 e 30 dias; saldo de empenho abaixo de duas
 * cobranças; cota em 80% e 100%; cobrança vencida; pagamento sem cobrança
 * correspondente. Os eventos vivem em {@code platform.alert_event}
 * (tabela da V9_1), com as regras semeadas na V10_1.
 *
 * <p>Garantia de nao-bloqueio: este servico so escreve em
 * {@code alert_event}. Ele nunca toca {@code tenant_lifecycle},
 * {@code saas_contract.status} ou qualquer flag de acesso — contrato
 * vencido, cota estourada e cobrança em atraso geram alerta e aviso, e
 * so a restricao manual (outro servico, outra rota) restringe. O
 * teste de integracao prova isso: apos avaliar sobre cobrança vencida,
 * contrato vencido e cota estourada, o ciclo de vida continua
 * intocado.
 *
 * <p>Resolucao: apos abrir os eventos da rodada, eventos abertos da
 * mesma regra cujo tenant nao esta mais na condicao sao resolvidos.
 * Eventos de outras regras (outros modulos) nunca sao tocados: o
 * UPDATE filtra pela regra.
 *
 * <p>Agendamento: esta tarefa expoe a avaliacao como servico + rota
 * manual (o financeiro dispara apos a rotina de conciliação). O
 * agendador periodico e ponto de extensao para a P2.1 (core V10,
 * job_runs com trava por tenant e ciclo), que pode chamar
 * {@link #evaluateAll()} sem mudar este codigo.
 */
@Service
public class SaaSContractAlerts {

    public record Report(int opened, int resolved, int tenantsWithAlerts) {
    }

    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public SaaSContractAlerts(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC());
    }

    SaaSContractAlerts(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Report evaluateAll() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        int opened = 0;
        int resolved = 0;
        Set<UUID> alarming = new HashSet<>();

        // -- Contrato a vencer (uma regra, severidade pela janela) --
        Map<UUID, String> expiring = new HashMap<>();
        List<Map<String, Object>> contracts = jdbc.queryForList(
                "SELECT tenant_id, ends_at FROM platform.saas_contract WHERE status = 'ACTIVE'");
        for (Map<String, Object> row : contracts) {
            UUID tenantId = (UUID) row.get("tenant_id");
            LocalDate endsAt = ((Date) row.get("ends_at")).toLocalDate();
            String severity = SaaSContractRules.expirySeverity(
                    SaaSContractRules.daysToExpiry(endsAt, today));
            if (severity != null) {
                expiring.put(tenantId, severity);
            }
        }
        for (Map.Entry<UUID, String> e : expiring.entrySet()) {
            opened += openEvent("saas.contract.expiring", e.getKey(), e.getValue());
            // Sobe (ou desce) a severidade do evento aberto com a janela.
            jdbc.update("UPDATE platform.alert_event SET severity = ? WHERE resolved_at IS NULL"
                            + " AND tenant_id = ? AND rule_id IN"
                            + " (SELECT id FROM platform.alert_rule WHERE rule_key = ?)",
                    e.getValue(), e.getKey(), "saas.contract.expiring");
        }
        resolved += resolveCleared("saas.contract.expiring", expiring.keySet());
        alarming.addAll(expiring.keySet());

        // -- Saldo de empenho abaixo de duas cobranças --
        Set<UUID> commitmentLow = new HashSet<>();
        List<Map<String, Object>> balances = jdbc.queryForList(
                "SELECT c.tenant_id, c.monthly_value, COALESCE(SUM(m.balance), 0) AS balance"
                        + " FROM platform.saas_contract c LEFT JOIN platform.saas_commitment m"
                        + " ON m.contract_id = c.id WHERE c.status = 'ACTIVE'"
                        + " GROUP BY c.tenant_id, c.monthly_value");
        for (Map<String, Object> row : balances) {
            BigDecimal inInvoices = SaaSContractRules.commitmentBalanceInInvoices(
                    (BigDecimal) row.get("balance"), (BigDecimal) row.get("monthly_value"));
            if (SaaSContractRules.commitmentLow(inInvoices)) {
                commitmentLow.add((UUID) row.get("tenant_id"));
            }
        }
        for (UUID tenantId : commitmentLow) {
            opened += openEvent("saas.contract.commitment_low", tenantId, "WARNING");
        }
        resolved += resolveCleared("saas.contract.commitment_low", commitmentLow);
        alarming.addAll(commitmentLow);

        // -- Cotas 80% / 100%, medidas pelo usage_ledger do mes --
        Set<UUID> quota80 = new HashSet<>();
        Set<UUID> quota100 = new HashSet<>();
        LocalDate month = today.withDayOfMonth(1);
        List<Map<String, Object>> quotaContracts = jdbc.queryForList(
                "SELECT tenant_id, quotas::text AS quotas FROM platform.saas_contract"
                        + " WHERE status = 'ACTIVE' AND quotas IS NOT NULL AND quotas::text <> '{}'");
        for (Map<String, Object> row : quotaContracts) {
            UUID tenantId = (UUID) row.get("tenant_id");
            Map<String, BigDecimal> quotas = parseQuotas((String) row.get("quotas"));
            for (Map.Entry<String, BigDecimal> q : quotas.entrySet()) {
                if (!q.getKey().startsWith("quota.")) {
                    continue;
                }
                String metric = q.getKey().substring("quota.".length());
                BigDecimal used = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(quantity), 0) FROM platform.usage_ledger"
                                + " WHERE tenant_id = ? AND period_month = ? AND metric_key = ?",
                        BigDecimal.class, tenantId, Date.valueOf(month), metric);
                BigDecimal ratio = SaaSContractRules.quotaRatio(used, q.getValue());
                Set<String> levels = SaaSContractRules.quotaAlertLevels(ratio);
                if (levels.contains("80")) {
                    quota80.add(tenantId);
                }
                if (levels.contains("100")) {
                    quota100.add(tenantId);
                }
            }
        }
        for (UUID tenantId : quota80) {
            opened += openEvent("saas.contract.quota_80", tenantId, "WARNING");
        }
        for (UUID tenantId : quota100) {
            opened += openEvent("saas.contract.quota_100", tenantId, "CRITICAL");
        }
        resolved += resolveCleared("saas.contract.quota_80", quota80);
        resolved += resolveCleared("saas.contract.quota_100", quota100);
        alarming.addAll(quota80);
        alarming.addAll(quota100);

        // -- Cobrança vencida --
        Set<UUID> overdue = new HashSet<>(jdbc.query(
                "SELECT DISTINCT c.tenant_id FROM platform.saas_invoice i"
                        + " JOIN platform.saas_contract c ON c.id = i.contract_id"
                        + " WHERE i.status IN ('OPEN', 'PARTIALLY_PAID') AND i.due_date < ?",
                (rs, i) -> rs.getObject(1, UUID.class), Date.valueOf(today)));
        for (UUID tenantId : overdue) {
            opened += openEvent("saas.invoice.overdue", tenantId, "WARNING");
        }
        resolved += resolveCleared("saas.invoice.overdue", overdue);
        alarming.addAll(overdue);

        // -- Pagamento sem cobrança correspondente --
        Set<UUID> unmatched = new HashSet<>(jdbc.query(
                "SELECT DISTINCT tenant_id FROM platform.saas_payment WHERE invoice_id IS NULL",
                (rs, i) -> rs.getObject(1, UUID.class)));
        for (UUID tenantId : unmatched) {
            opened += openEvent("saas.payment.unmatched", tenantId, "WARNING");
        }
        resolved += resolveCleared("saas.payment.unmatched", unmatched);
        alarming.addAll(unmatched);

        return new Report(opened, resolved, alarming.size());
    }

    /** Abre o evento sem duplicar (indice unico parcial da V9_1); devolve 1 se abriu. */
    private int openEvent(String ruleKey, UUID tenantId, String severity) {
        return jdbc.update("""
                INSERT INTO platform.alert_event (rule_id, tenant_id, severity, status)
                SELECT id, ?, ?, 'OPEN' FROM platform.alert_rule WHERE rule_key = ?
                ON CONFLICT (rule_id, (COALESCE(tenant_id, ?::UUID)))
                    WHERE resolved_at IS NULL DO NOTHING
                """, tenantId, severity, ruleKey, ZERO_UUID);
    }

    /** Resolve eventos abertos da regra cujo tenant nao esta mais na condicao. */
    private int resolveCleared(String ruleKey, Set<UUID> stillAlarming) {
        if (stillAlarming.isEmpty()) {
            return jdbc.update("""
                    UPDATE platform.alert_event SET status = 'RESOLVED', resolved_at = NOW()
                    WHERE resolved_at IS NULL AND rule_id IN
                        (SELECT id FROM platform.alert_rule WHERE rule_key = ?)
                    """, ruleKey);
        }
        String placeholders = String.join(",", stillAlarming.stream().map(t -> "?").toList());
        Object[] args = new Object[stillAlarming.size() + 1];
        args[0] = ruleKey;
        int i = 1;
        for (UUID tenantId : stillAlarming) {
            args[i++] = tenantId;
        }
        return jdbc.update("""
                UPDATE platform.alert_event SET status = 'RESOLVED', resolved_at = NOW()
                WHERE resolved_at IS NULL AND rule_id IN
                    (SELECT id FROM platform.alert_rule WHERE rule_key = ?)
                AND tenant_id NOT IN (
                """ + placeholders + ")",
                args);
    }

    /**
     * Leitura tolerante das cotas JSONB: chaves quota.* com numero.
     * Valor fora do formato e ignorado (a validacao estrita vive na
     * criacao do contrato, nao na avaliacao de alertas).
     */
    static Map<String, BigDecimal> parseQuotas(String quotasJson) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (quotasJson == null || quotasJson.isBlank()) {
            return out;
        }
        String body = quotasJson.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String entry : body.split(",")) {
            String[] kv = entry.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().replaceAll("^\"|\"$", "");
            String value = kv[1].trim().replaceAll("^\"|\"$", "");
            try {
                out.put(key, new BigDecimal(value));
            } catch (NumberFormatException e) {
                // Cota sem numero: ignorada na avaliacao, sem quebrar o resto.
            }
        }
        return out;
    }
}
