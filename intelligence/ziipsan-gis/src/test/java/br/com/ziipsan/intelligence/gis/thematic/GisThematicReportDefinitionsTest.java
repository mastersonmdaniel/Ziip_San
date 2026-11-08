package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-08 (P3.27): as definições GIS-001/GIS-002 no contrato do
 * núcleo da P3.22 — a própria construção valida código, view
 * {@code rpt_*} e o padrão da permissão; aqui fica a conferência
 * de que as colunas declaradas são as das views da V13_13 e de que
 * nenhuma é pessoal (extensão e precisão são de ativos, §12).
 */
class GisThematicReportDefinitionsTest {

    @Test
    @DisplayName("GIS-001: extensão sobre rpt_gis_extensao_rede, com extensao_m somável e filtros do aceite")
    void extensionDefinition() {
        ReportDefinition def = GisThematicReportDefinitions.extension();
        assertThat(def.code()).isEqualTo("GIS-001");
        assertThat(def.origin()).isEqualTo(ReportOrigin.EXTERNAL);
        assertThat(def.viewName()).isEqualTo("rpt_gis_extensao_rede");
        assertThat(def.requiredPermission()).isEqualTo("gis.gis-001:export");
        assertThat(def.columns()).extracting(c -> c.name())
                .containsExactly("dominio", "funcao", "material", "dn_mm", "decada_inicio",
                        "setor_codigo", "setor", "extensao_m", "arcos");
        assertThat(def.column("extensao_m").aggregable()).isTrue();
        assertThat(def.column("arcos").aggregable()).isTrue();
        assertThat(def.column("dominio").aggregable()).isFalse();
        assertThat(def.columns()).allMatch(c -> !c.personal());
        for (String filter : List.of("dominio", "funcao", "material", "dn_mm",
                "decada_inicio", "setor_codigo")) {
            assertThat(def.filterable(filter)).isTrue();
        }
        assertThat(def.filterable("tenant_id")).isFalse();
    }

    @Test
    @DisplayName("GIS-002: precisão sobre rpt_gis_precisao_cadastro, agrupável por classe e método")
    void precisionDefinition() {
        ReportDefinition def = GisThematicReportDefinitions.precision();
        assertThat(def.code()).isEqualTo("GIS-002");
        assertThat(def.viewName()).isEqualTo("rpt_gis_precisao_cadastro");
        assertThat(def.requiredPermission()).isEqualTo("gis.gis-002:export");
        assertThat(def.columns()).extracting(c -> c.name())
                .containsExactly("classe", "metodo_captura", "grupo_precisao", "dominio", "feicoes");
        assertThat(def.column("feicoes").aggregable()).isTrue();
        assertThat(def.columns()).allMatch(c -> !c.personal());
        assertThat(def.filterable("classe")).isTrue();
        assertThat(def.filterable("metodo_captura")).isTrue();
        assertThat(def.filterable("grupo_precisao")).isTrue();
    }

    @Test
    @DisplayName("as duas definições, sem colisão de código nem de view")
    void bothDefinitions() {
        List<ReportDefinition> all = GisThematicReportDefinitions.definitions();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(ReportDefinition::code).doesNotHaveDuplicates();
        assertThat(all).extracting(ReportDefinition::viewName).doesNotHaveDuplicates();
    }
}
