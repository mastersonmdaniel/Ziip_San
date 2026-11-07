package br.com.ziipsan.reporting.query;

import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportQueryBuilderTest {

    private final ReportQueryBuilder builder = new ReportQueryBuilder();
    private final ReportRegistry registry = new ReportRegistry();

    @Test
    void montaSqlSoComIdentificadoresDeclaradosEValoresComoParametros() {
        var definition = registry.find("RPT-001").orElseThrow();
        var query = new ReportQuery(
                Map.of("data", ReportQuery.FilterValue.range("2026-10-01", "2026-10-31")),
                "valor_liquido", false, List.of());
        var built = builder.build(definition, query);
        assertThat(built.sql()).contains("FROM rpt_arrecadacao_diaria")
                .contains("tenant_id = ?").contains("data >= ?").contains("data <= ?")
                .contains("ORDER BY valor_liquido DESC").contains("LIMIT ?");
        assertThat(built.sql()).doesNotContain("2026-10-01");
        assertThat(built.params()).containsExactly("__TENANT__", "2026-10-01", "2026-10-31", 10_001);
    }

    @Test
    void agrupamentoSomaNumericasEMantemDimensoes() {
        var definition = registry.find("RPT-001").orElseThrow();
        var built = builder.build(definition,
                new ReportQuery(Map.of(), null, true, List.of("canal")));
        assertThat(built.sql()).contains("SUM(valor_bruto) AS valor_bruto")
                .contains("GROUP BY canal");
    }

    @Test
    void percentualNaoAtendidoNuncaViraSomaNoAgrupamento() {
        // Correcao R2: percentual_nao_atendido (DECIMAL, nao agregavel)
        // agrupado vira MAX, nunca SUM; os contadores LONG seguem SUM.
        var definition = registry.find("RPT-027").orElseThrow();
        var built = builder.build(definition,
                new ReportQuery(Map.of(), null, true, List.of("sistema")));
        assertThat(built.sql()).contains("SUM(encerrados) AS encerrados")
                .contains("SUM(nao_atendidos_no_prazo) AS nao_atendidos_no_prazo")
                .contains("MAX(percentual_nao_atendido) AS percentual_nao_atendido")
                .doesNotContain("SUM(percentual_nao_atendido)");
    }

    @Test
    void recusaFiltroOrdenacaoEAgrupamentoNaoDeclarados() {
        var definition = registry.find("RPT-001").orElseThrow();
        assertThatThrownBy(() -> builder.build(definition, new ReportQuery(
                Map.of("canal; DROP TABLE payments", ReportQuery.FilterValue.eq("X")),
                null, true, List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.build(definition, new ReportQuery(
                Map.of(), "senha", true, List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.build(definition, new ReportQuery(
                Map.of(), null, true, List.of("senha")))).isInstanceOf(IllegalArgumentException.class);
        // canal nao e filtro declarado da RPT-001 (so data): tambem recusa
        assertThatThrownBy(() -> builder.build(definition, new ReportQuery(
                Map.of("canal", ReportQuery.FilterValue.eq("PIX")), null, true, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
