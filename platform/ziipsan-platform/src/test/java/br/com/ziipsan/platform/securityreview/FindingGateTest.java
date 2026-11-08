package br.com.ziipsan.platform.securityreview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.8: nenhum achado alto aberto — o portao nao tem caminho de aceite silencioso. */
class FindingGateTest {

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/security-review/" + name)) {
            if (in == null) {
                throw new IllegalStateException("fixture ausente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("relatorio ZAP sintetico sem altos passa; com alto aberto, bloqueia")
    void zapGate() throws IOException {
        var semAltos = FindingGate.evaluate(FindingGate.fromZapReport(fixture("zap-report-sem-altos.json")));
        assertThat(semAltos.passed()).isTrue();
        var comAlto = FindingGate.evaluate(FindingGate.fromZapReport(fixture("zap-report-com-alto-aberto.json")));
        assertThat(comAlto.passed()).isFalse();
        assertThat(comAlto.blocking()).hasSize(1);
        assertThat(comAlto.blocking().get(0).severity()).isEqualTo(FindingGate.Severity.HIGH);
    }

    @Test
    @DisplayName("alto resolvido ou falso positivo nao bloqueia; critico aberto bloqueia")
    void resolvidoNaoBloqueia() {
        var ok = FindingGate.evaluate(List.of(
                new FindingGate.Finding("F1", "x", FindingGate.Severity.HIGH, FindingGate.Status.RESOLVED, "manual"),
                new FindingGate.Finding("F2", "y", FindingGate.Severity.HIGH, FindingGate.Status.FALSE_POSITIVE, "manual"),
                new FindingGate.Finding("F3", "z", FindingGate.Severity.MEDIUM, FindingGate.Status.OPEN, "manual")));
        assertThat(ok.passed()).isTrue();
        var critico = FindingGate.evaluate(List.of(
                new FindingGate.Finding("F4", "w", FindingGate.Severity.CRITICAL, FindingGate.Status.OPEN, "manual")));
        assertThat(critico.passed()).isFalse();
    }

    @Test
    @DisplayName("relatorio malformado falha fechado (A10: condicao excepcional nao vira verde)")
    void relatorioMalformadoFalhaFechado() {
        assertThatThrownBy(() -> FindingGate.fromZapReport("{isto nao e json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("relatorio vazio ou sem estrutura falha FECHADO, nunca verde (D4: {} e site vazio/nao-lista/sem alertas)")
    void relatorioVazioFalhaFechado() {
        // {} — sem a chave site
        assertThatThrownBy(() -> FindingGate.fromZapReport("{}"))
                .isInstanceOf(IllegalStateException.class);
        // site vazio — nenhum site varrido nao e evidencia
        assertThatThrownBy(() -> FindingGate.fromZapReport("{\"site\":[]}"))
                .isInstanceOf(IllegalStateException.class);
        // site nao-lista — o espelho Python ja bloqueava; o Java passava (divergencia D4 unificada)
        assertThatThrownBy(() -> FindingGate.fromZapReport("{\"site\":{}}"))
                .isInstanceOf(IllegalStateException.class);
        // site sem alerts — relatorio incompleto
        assertThatThrownBy(() -> FindingGate.fromZapReport("{\"site\":[{\"@name\":\"http://localhost\"}]}"))
                .isInstanceOf(IllegalStateException.class);
        // alerts vazio — zero alertas no total nao e evidencia de varredura
        assertThatThrownBy(() -> FindingGate.fromZapReport("{\"site\":[{\"alerts\":[]}]}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
