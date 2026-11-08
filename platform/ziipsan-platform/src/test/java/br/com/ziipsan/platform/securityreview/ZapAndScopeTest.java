package br.com.ziipsan.platform.securityreview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.8: plano DAST e escopo do pentest externo — definicao completa, execucao na LC.8. */
class ZapAndScopeTest {

    private ZapScanPlan validPlan() {
        return new ZapScanPlan(true, Set.of("portal", "backoffice"),
                Set.of("sintetico.portal@example.invalid", "sintetico.backoffice@example.invalid"),
                true, false, "http://127.0.0.1:8080");
    }

    @Test
    @DisplayName("plano autenticado nos dois contextos, usuarios sinteticos e alvo local: valido")
    void planoValido() {
        assertThat(validPlan().problems()).isEmpty();
        assertThatCode(() -> validPlan().requireValid()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("varredura anonima, sem contexto, com usuario real ou contra producao: recusada")
    void planosRecusados() {
        assertThat(new ZapScanPlan(false, Set.of("portal", "backoffice"),
                Set.of("sintetico.portal@example.invalid"), true, false, "http://127.0.0.1").problems())
                .anyMatch(p -> p.contains("autenticada"));
        assertThat(new ZapScanPlan(true, Set.of("portal"),
                Set.of("sintetico.portal@example.invalid"), true, false, "http://127.0.0.1").problems())
                .anyMatch(p -> p.contains("backoffice"));
        assertThat(new ZapScanPlan(true, Set.of("portal", "backoffice"),
                Set.of("operador@empresa.com.br"), true, false, "http://127.0.0.1").problems())
                .anyMatch(p -> p.contains("sintetico"));
        assertThatThrownBy(() -> new ZapScanPlan(true, Set.of("portal", "backoffice"),
                Set.of("sintetico.portal@example.invalid"), true, true, "https://producao.invalid").requireValid())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("URL de producao com productionTarget=false e recusada: a localidade vem da propria URL (D3)")
    void planoComUrlNaoLocalSemFlagERecusado() {
        var plano = new ZapScanPlan(true, Set.of("portal", "backoffice"),
                Set.of("sintetico.portal@example.invalid", "sintetico.backoffice@example.invalid"),
                true, false, "https://app.exemplo.com.br");
        assertThat(plano.problems()).anyMatch(p -> p.contains("ambiente local"));
        assertThatThrownBy(plano::requireValid).isInstanceOf(IllegalStateException.class);
        // Host que apenas COMECA com localhost nao e local.
        assertThat(new ZapScanPlan(true, Set.of("portal", "backoffice"),
                Set.of("sintetico.portal@example.invalid"), true, false,
                "http://localhost.exemplo.com.br").problems())
                .anyMatch(p -> p.contains("ambiente local"));
        // localhost com porta continua valido como alvo local.
        assertThat(new ZapScanPlan(true, Set.of("portal", "backoffice"),
                Set.of("sintetico.portal@example.invalid", "sintetico.backoffice@example.invalid"),
                true, false, "http://localhost:8080").problems()).isEmpty();
    }

    @Test
    @DisplayName("escopo LC.8 completo com termos definidos e execucao na LC.8: definicao valida")
    void escopoValido() {
        var terms = new PentestExternalScope.ContractingTerms(true, true, true, true, false);
        assertThat(PentestExternalScope.validateDefinition(
                PentestExternalScope.REQUIRED_SCOPE_ITEMS, terms, "LC.8")).isEmpty();
    }

    @Test
    @DisplayName("execucao fora da LC.8, escopo incompleto ou fornecedor inventado: recusados")
    void escopoRecusado() {
        var terms = new PentestExternalScope.ContractingTerms(true, true, true, true, false);
        assertThat(PentestExternalScope.validateDefinition(
                PentestExternalScope.REQUIRED_SCOPE_ITEMS, terms, "P3.8"))
                .anyMatch(p -> p.contains("LC.8"));
        assertThat(PentestExternalScope.validateDefinition(Set.of("cofre"), terms, "LC.8"))
                .anyMatch(p -> p.contains("incompleto"));
        var comFornecedor = new PentestExternalScope.ContractingTerms(true, true, true, true, true);
        assertThat(PentestExternalScope.validateDefinition(
                PentestExternalScope.REQUIRED_SCOPE_ITEMS, comFornecedor, "LC.8"))
                .anyMatch(p -> p.contains("fornecedor"));
    }
}
