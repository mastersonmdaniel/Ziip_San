package br.com.ziipsan.platform.observability;

import java.util.Objects;

/**
 * P3.11 (PLT-23): regra de alerta da Central (tabela {@code platform.alert_rule},
 * core {@code V9_1}, P1.17) no nível da aplicação.
 *
 * <p>A {@code V9_1} já exige {@code owner NOT NULL}, mas não tem coluna
 * {@code runbook_url} — e o plano v3.0 §3 não reserva versão Flyway para a
 * P3.11, portanto esta tarefa não cria migration (ver RELATORIO-P3.11.md).
 * A obrigatoriedade dos dois campos é garantida aqui, na única porta de
 * construção da regra, e no catálogo versionado
 * {@code docs/observability/alert-rules.json} validado por
 * {@link AlertRuleCatalog}: regra sem {@code runbook_url} ou sem
 * responsável é recusada com erro explícito, nunca aceita com padrão.
 */
public record AlertRule(
        String ruleKey,
        String metricKey,
        String comparator,
        double threshold,
        int consecutiveReadings,
        String severity,
        String scope,
        String owner,
        String runbookUrl) {

    public AlertRule {
        if (ruleKey == null || ruleKey.isBlank()) {
            throw new IllegalArgumentException("rule_key é obrigatório");
        }
        if (metricKey == null || metricKey.isBlank()) {
            throw new IllegalArgumentException("metric_key é obrigatório");
        }
        if (comparator == null || !java.util.Set.of(">", ">=", "<", "<=", "=").contains(comparator)) {
            throw new IllegalArgumentException("comparator inválido: " + comparator);
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("threshold deve ser finito");
        }
        if (consecutiveReadings < 1) {
            throw new IllegalArgumentException("consecutive_readings deve ser >= 1");
        }
        if (severity == null || !java.util.Set.of("INFO", "WARNING", "CRITICAL").contains(severity)) {
            throw new IllegalArgumentException("severity inválida: " + severity);
        }
        if (scope == null || !java.util.Set.of("GLOBAL", "TENANT").contains(scope)) {
            throw new IllegalArgumentException("scope inválido: " + scope);
        }
        // Acceptance P3.11: runbook e responsável obrigatórios.
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException(
                    "Regra " + ruleKey + " sem responsável (owner): recusa da P3.11");
        }
        if (runbookUrl == null || runbookUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Regra " + ruleKey + " sem runbook_url: recusa da P3.11 — todo alerta precisa de runbook");
        }
        if (!runbookUrl.startsWith("docs/operacao/runbooks/") && !runbookUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "runbook_url da regra " + ruleKey + " deve apontar para docs/operacao/runbooks/ "
                            + "ou URL https da página de runbooks: " + runbookUrl);
        }
        Objects.requireNonNull(ruleKey);
    }
}
