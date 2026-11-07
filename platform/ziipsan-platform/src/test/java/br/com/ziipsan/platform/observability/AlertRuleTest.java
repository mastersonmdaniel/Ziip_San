package br.com.ziipsan.platform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleTest {

    private static AlertRule valid(String owner, String runbookUrl) {
        return new AlertRule("api.error_rate.high", "platform.outbox.oldest_pending_age_s", ">", 0.01, 3,
                "CRITICAL", "GLOBAL", owner, runbookUrl);
    }

    @Test
    @DisplayName("P3.11: regra sem runbook_url é recusada")
    void refusesMissingRunbook() {
        assertThatThrownBy(() -> valid("platform", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runbook_url");
        assertThatThrownBy(() -> valid("platform", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runbook_url");
    }

    @Test
    @DisplayName("P3.11: regra sem responsável é recusada")
    void refusesMissingOwner() {
        assertThatThrownBy(() -> valid(null, "docs/operacao/runbooks/RB-01-api-indisponivel.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsável");
        assertThatThrownBy(() -> valid("", "docs/operacao/runbooks/RB-01-api-indisponivel.md"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P3.11: runbook fora do caminho de runbooks e sem https é recusado")
    void refusesRunbookOutsideCatalog() {
        assertThatThrownBy(() -> valid("platform", "http://inseguro.invalid/runbook"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid("platform", "runbooks/solto.md"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(valid("platform", "docs/operacao/runbooks/RB-01-api-indisponivel.md").runbookUrl())
                .startsWith("docs/operacao/runbooks/");
    }

    @Test
    @DisplayName("P3.11: catálogo JSON (via Json do core) recusa regra sem runbook e aceita catálogo completo")
    void catalogRefusesRuleWithoutRunbook() {
        String semRunbook = """
                [{"rule_key":"x.y","metric_key":"platform.outbox.oldest_pending_age_s","comparator":">",
                  "threshold":0.01,"severity":"CRITICAL","scope":"GLOBAL","owner":"platform"}]
                """;
        assertThatThrownBy(() -> AlertRuleCatalog.fromJson(semRunbook))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runbook_url");

        String completo = """
                [{"rule_key":"x.y","metric_key":"platform.outbox.oldest_pending_age_s","comparator":">",
                  "threshold":0.01,"consecutive_readings":3,"severity":"CRITICAL","scope":"GLOBAL",
                  "owner":"platform","runbook_url":"docs/operacao/runbooks/RB-01-api-indisponivel.md"}]
                """;
        List<AlertRule> rules = AlertRuleCatalog.fromJson(completo);
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).owner()).isEqualTo("platform");
    }

    @Test
    @DisplayName("P3.11 (pós-revisão P-4): catálogo sem consecutive_readings é recusado — campo ausente é erro, sem fallback")
    void catalogRefusesMissingConsecutiveReadings() {
        String semConsecutive = """
                [{"rule_key":"x.y","metric_key":"platform.outbox.oldest_pending_age_s","comparator":">",
                  "threshold":0.01,"severity":"CRITICAL","scope":"GLOBAL",
                  "owner":"platform","runbook_url":"docs/operacao/runbooks/RB-01-api-indisponivel.md"}]
                """;
        assertThatThrownBy(() -> AlertRuleCatalog.fromJson(semConsecutive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive_readings");
    }
}
