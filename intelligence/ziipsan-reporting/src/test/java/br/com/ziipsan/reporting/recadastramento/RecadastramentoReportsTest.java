package br.com.ziipsan.reporting.recadastramento;

import br.com.ziipsan.reporting.registry.ExternalReportRegistrar;
import br.com.ziipsan.reporting.registry.ReportOrigin;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-16 — os cinco relatorios do recadastramento (SG-910 a SG-914)
 * sobre o nucleo da P3.22: definicoes validas (view rpt_*, permissao
 * por codigo, colunas declaradas) e registro externo por tenant, sem
 * tocar o catalogo de fabrica.
 */
class RecadastramentoReportsTest {

    @Test
    void cincoDefinicoesValidasNaOrdemDosItensDoSamae() {
        var definitions = RecadastramentoReports.all();
        assertThat(definitions).hasSize(5);
        assertThat(definitions).extracting(d -> d.code())
                .containsExactly("REC-001", "REC-002", "REC-003", "REC-004", "REC-005");
        assertThat(definitions).extracting(d -> d.viewName())
                .containsExactly("rpt_recadastramento_differences",
                        "rpt_recadastramento_errors",
                        "rpt_recadastramento_incorrect",
                        "rpt_recadastramento_recent_units",
                        "rpt_recadastramento_collector_efficiency");
        assertThat(definitions).allMatch(d -> d.origin() == ReportOrigin.EXTERNAL);
        assertThat(definitions).allMatch(d -> !d.columns().isEmpty());
    }

    @Test
    void registroNoNucleoEfetivaPorTenantENaoDuplica() {
        var registry = new ReportRegistry();
        var registrar = new ExternalReportRegistrar(registry);
        var tenant = UUID.randomUUID();

        RecadastramentoReports.registerAll(registrar, tenant);

        for (String code : new String[] {"REC-001", "REC-002", "REC-003", "REC-004", "REC-005"}) {
            assertThat(registry.find(code, tenant)).isPresent();
            // Outro tenant nao enxerga o registro.
            assertThat(registry.find(code, UUID.randomUUID())).isEmpty();
        }
        // Registro repetido do mesmo codigo e recusado.
        assertThatThrownBy(() -> RecadastramentoReports.registerAll(registrar, tenant))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void valoresDoRelatorioDeIncorretosSaoPessoaisEOsDemaisNaoExpoemValor() {
        var incorrect = RecadastramentoReports.incorrect();
        assertThat(incorrect.columns())
                .filteredOn(c -> c.name().startsWith("valor_"))
                .hasSize(3)
                .allMatch(c -> c.personal());
        // Diferencas, erros, recentes e eficiencia nao expoem valor de campo.
        for (var definition : java.util.List.of(
                RecadastramentoReports.differences(), RecadastramentoReports.errors(),
                RecadastramentoReports.recentUnits(), RecadastramentoReports.collectorEfficiency())) {
            assertThat(definition.columns()).allMatch(c -> !c.personal());
        }
    }

    @Test
    void taxaDeAprovacaoDaEficienciaNaoESomavelEFiltrosBatComAsViews() {
        var efficiency = RecadastramentoReports.collectorEfficiency();
        assertThat(efficiency.column("taxa_aprovacao").aggregable()).isFalse();
        assertThat(efficiency.filterable("referencia")).isTrue();
        assertThat(RecadastramentoReports.differences().filterable("referencia")).isTrue();
        assertThat(RecadastramentoReports.recentUnits().filterable("unidade_criada_em")).isTrue();
        assertThat(RecadastramentoReports.recentUnits().columns()).extracting(c -> c.name())
                .containsExactly("unidade_criada_em", "unidade_id", "regra", "severidade", "data_achado");
    }
}
