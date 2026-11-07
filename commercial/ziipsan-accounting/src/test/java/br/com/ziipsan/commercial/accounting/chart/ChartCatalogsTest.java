package br.com.ziipsan.commercial.accounting.chart;

import br.com.ziipsan.commercial.accounting.chart.ChartCatalogs.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ChartCatalogsTest {
    @Test @DisplayName("virada de exercicio: 2026 nao usa o PCASP 2027; 2027 usa (Portaria STN/MF 2.520/2026)")
    void exerciseTurnover() {
        assertThat(ChartCatalogs.pcaspVersionForExercise(2026)).doesNotContain("2.520/2026");
        assertThat(ChartCatalogs.pcaspVersionForExercise(2027)).isEqualTo(ChartCatalogs.PCASP_2027_VERSION);
        assertThat(ChartCatalogs.MCRSB_VERSION).contains("302/2026");
    }
    @Test @DisplayName("de-para por exercicio: a mesma conta MCRSB muda de conta PCASP na virada")
    void mappingByExercise() {
        var maps = List.of(
            new Mapping("MCRSB-1", "PCASP-2026-A", TenantChartKind.PCASP, "PCASP exercicio 2026", 2026, 2026, null, null),
            new Mapping("MCRSB-1", "PCASP-2027-B", TenantChartKind.PCASP, ChartCatalogs.PCASP_2027_VERSION, 2027, null, null, null));
        assertThat(ChartCatalogs.resolve(maps, "MCRSB-1", TenantChartKind.PCASP, 2026, LocalDate.of(2026,6,1)).tenantAccountCode()).isEqualTo("PCASP-2026-A");
        assertThat(ChartCatalogs.resolve(maps, "MCRSB-1", TenantChartKind.PCASP, 2027, LocalDate.of(2027,1,15)).tenantAccountCode()).isEqualTo("PCASP-2027-B");
        assertThat(ChartCatalogs.resolve(maps, "MCRSB-1", TenantChartKind.PCASP, 2027, LocalDate.of(2027,1,15)).catalogVersion()).isEqualTo(ChartCatalogs.PCASP_2027_VERSION);
    }
    @Test @DisplayName("de-para respeita o tipo do plano: PCASP e societario para a mesma conta MCRSB nao colidem")
    void mappingRespectsTenantKind() {
        var maps = List.of(
            new Mapping("MCRSB-1", "PCASP-A", TenantChartKind.PCASP, "PCASP exercicio 2026", 2026, null, null, null),
            new Mapping("MCRSB-1", "SOC-A", TenantChartKind.SOCIETARIO, "Societario 2026", 2026, null, null, null));
        assertThat(ChartCatalogs.resolve(maps, "MCRSB-1", TenantChartKind.PCASP, 2026, LocalDate.of(2026,6,1)).tenantAccountCode()).isEqualTo("PCASP-A");
        assertThat(ChartCatalogs.resolve(maps, "MCRSB-1", TenantChartKind.SOCIETARIO, 2026, LocalDate.of(2026,6,1)).tenantAccountCode()).isEqualTo("SOC-A");
    }
    @Test @DisplayName("de-paras vigentes sobrepostos para a mesma chave sao ambiguidade recusada")
    void overlappingMappingsRefused() {
        var maps = List.of(
            new Mapping("MCRSB-1", "PCASP-A", TenantChartKind.PCASP, "PCASP exercicio 2026", 2026, null, null, null),
            new Mapping("MCRSB-1", "PCASP-B", TenantChartKind.PCASP, "PCASP exercicio 2026", 2026, null, null, null));
        assertThatThrownBy(() -> ChartCatalogs.resolve(maps, "MCRSB-1", TenantChartKind.PCASP, 2026, LocalDate.of(2026,6,1)))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("codigos em branco no de-para sao recusados")
    void blankCodesRefused() {
        assertThatThrownBy(() -> new Mapping(" ", "PCASP-A", TenantChartKind.PCASP, "v", 2026, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Mapping("MCRSB-1", "", TenantChartKind.PCASP, "v", 2026, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
