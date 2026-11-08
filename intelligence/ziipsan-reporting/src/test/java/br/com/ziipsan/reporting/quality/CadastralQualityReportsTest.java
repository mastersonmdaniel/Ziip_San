package br.com.ziipsan.reporting.quality;

import br.com.ziipsan.reporting.registry.ExternalReportRegistrar;
import br.com.ziipsan.reporting.registry.ReportOrigin;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.32 - relatorios de qualidade sobre o nucleo da P3.22: definicoes
 * validas (view rpt_*, permissao por codigo, colunas declaradas) e registro
 * externo por tenant, sem tocar o catalogo de fabrica.
 */
class CadastralQualityReportsTest {

    @Test
    void definicoesSaoValidasEApontamParaAsViewsDaMigration() {
        var definitions = CadastralQualityReports.all();
        assertThat(definitions).hasSize(3);
        assertThat(definitions).extracting(d -> d.code())
                .containsExactly("QUAL-001", "QUAL-002", "QUAL-003");
        assertThat(definitions).extracting(d -> d.viewName())
                .containsExactly("rpt_cadastral_quality_route",
                        "rpt_cadastral_quality_sector", "rpt_cadastral_quality_trend");
        assertThat(definitions).allMatch(d -> d.origin() == ReportOrigin.EXTERNAL);
        assertThat(definitions).allMatch(d -> !d.columns().isEmpty());
        // Nenhuma coluna pessoal: os relatorios sao agregados por grupo.
        assertThat(definitions).allMatch(d -> d.columns().stream().noneMatch(c -> c.personal()));
    }

    @Test
    void registroNoNucleoEfetivaPorTenantENaoDuplica() {
        var registry = new ReportRegistry();
        var registrar = new ExternalReportRegistrar(registry);
        var tenant = UUID.randomUUID();

        CadastralQualityReports.registerAll(registrar, tenant);

        assertThat(registry.find("QUAL-001", tenant)).isPresent();
        assertThat(registry.find("QUAL-002", tenant)).isPresent();
        assertThat(registry.find("QUAL-003", tenant)).isPresent();
        // Outro tenant nao enxerga o registro.
        assertThat(registry.find("QUAL-001", UUID.randomUUID())).isEmpty();
        // Registro repetido do mesmo codigo e recusado.
        assertThatThrownBy(() -> CadastralQualityReports.registerAll(registrar, tenant))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void colunasDaTendenciaBatComAView() {
        var trend = CadastralQualityReports.trend();
        assertThat(trend.columns()).extracting(c -> c.name())
                .containsExactly("referencia", "unidades_avaliadas", "achados",
                        "pontos_penalidade", "pontuacao", "completude");
        assertThat(trend.filterable("referencia")).isTrue();
    }
}
