package br.com.ziipsan.platform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.11 (correção pós-revisão P-5): valida o catálogo ENTREGUE
 * ({@code docs/observability/alert-rules.json}, o arquivo real do repo —
 * não strings inline) de ponta a ponta: toda regra carrega pelo
 * {@link AlertRuleCatalog} (que recusa owner/runbook ausentes), tem
 * responsável e runbook, o arquivo do runbook existe, e toda
 * {@code metric_key} pertence ao catálogo fechado de
 * {@code platform.metric_definition} (V9_1, 8 chaves {@code platform.*}) —
 * fora dele, o INSERT em {@code platform.alert_rule} viola a FK.
 *
 * <p>É este teste que impede a regressão dos defeitos P-1/P-2 da revisão:
 * regras de burn sobre {@code http.server.availability} (métrica
 * inexistente) e regras de PSP/SEFAZ/certificado/réplica sobre a métrica
 * genérica auto-referente {@code platform.alerts.open} foram retiradas
 * do catálogo executável para
 * {@code docs/observability/alert-rules-pendentes.md} e não podem voltar
 * sem métrica real no catálogo fechado.
 */
class AlertRuleDeliveredCatalogTest {

    /** As 8 chaves inseridas em {@code platform.metric_definition} pela V9_1. */
    private static final Set<String> KNOWN_METRIC_KEYS = Set.of(
            "platform.tenants.total",
            "platform.tenants.active",
            "platform.outbox.pending",
            "platform.outbox.dead",
            "platform.outbox.oldest_pending_age_s",
            "platform.alerts.open",
            "platform.onboarding.pending_steps",
            "platform.collector.last_run_age_s");

    /** Sobe a partir do diretório de trabalho até achar a raiz do repo. */
    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("docs/observability/alert-rules.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "docs/observability/alert-rules.json não encontrado subindo a partir de user.dir — "
                        + "o teste deve rodar dentro do repo");
    }

    private static List<AlertRule> deliveredRules() throws IOException {
        String json = Files.readString(
                repoRoot().resolve("docs/observability/alert-rules.json"), StandardCharsets.UTF_8);
        return AlertRuleCatalog.fromJson(json);
    }

    @Test
    @DisplayName("P3.11: o alert-rules.json entregue carrega e tem exatamente as 2 regras executáveis esperadas")
    void deliveredCatalogLoads() throws IOException {
        List<AlertRule> rules = deliveredRules();
        assertThat(rules).hasSize(2);
        assertThat(rules).extracting(AlertRule::ruleKey)
                .containsExactlyInAnyOrder(
                        "central.collector.stale",
                        "platform.outbox.oldest_pending_age_s.high");
    }

    @Test
    @DisplayName("P3.11: toda regra entregue tem owner, runbook_url em docs/operacao/runbooks/ e o arquivo do runbook existe")
    void everyDeliveredRuleHasOwnerAndExistingRunbook() throws IOException {
        Path root = repoRoot();
        for (AlertRule rule : deliveredRules()) {
            assertThat(rule.owner()).as("owner da regra %s", rule.ruleKey()).isNotBlank();
            assertThat(rule.runbookUrl()).as("runbook_url da regra %s", rule.ruleKey())
                    .isNotBlank()
                    .startsWith("docs/operacao/runbooks/");
            assertThat(root.resolve(rule.runbookUrl()))
                    .as("arquivo de runbook da regra %s", rule.ruleKey())
                    .isRegularFile();
        }
    }

    @Test
    @DisplayName("P3.11 (P-1/P-2): toda metric_key entregue pertence ao catálogo fechado da V9_1 e nenhuma regra usa a métrica genérica")
    void everyDeliveredMetricKeyExistsInClosedCatalog() throws IOException {
        List<AlertRule> rules = deliveredRules();
        assertThat(rules).extracting(AlertRule::metricKey)
                .as("metric_key fora de platform.metric_definition violaria a FK de alert_rule")
                .allSatisfy(key -> assertThat(KNOWN_METRIC_KEYS)
                        .as("catálogo fechado da V9_1")
                        .contains(key));
        assertThat(rules).extracting(AlertRule::metricKey)
                .doesNotContain("http.server.availability", "http.server.errors", "platform.alerts.open")
                .doesNotHaveDuplicates();
    }
}
