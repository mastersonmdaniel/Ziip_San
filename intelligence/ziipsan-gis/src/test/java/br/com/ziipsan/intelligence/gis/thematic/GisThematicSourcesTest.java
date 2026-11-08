package br.com.ziipsan.intelligence.gis.thematic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-08 (P3.27): a lista fechada de fontes — é ela que impede SQL
 * montado de nome vindo de fora e que carrega a matriz de
 * privacidade do §12 (pessoal × publicável).
 */
class GisThematicSourcesTest {

    @Test
    @DisplayName("toda fonte resolve por kind e aponta para a view da V13_13")
    void allSourcesResolve() {
        for (GisThematicSources source : GisThematicSources.values()) {
            assertThat(GisThematicSources.byKind(source.name())).contains(source);
            assertThat(source.viewName()).startsWith("gis_thematic_");
        }
        assertThat(GisThematicSources.byKind("gis_arc")).isEmpty();
        assertThat(GisThematicSources.byKind(null)).isEmpty();
        assertThat(GisThematicSources.byKind("BOGUS")).isEmpty();
    }

    @Test
    @DisplayName("matriz do §12: OS nunca publica; rede e precisão publicam sem ser pessoal; unidade é pessoal publicável agregada")
    void privacyMatrix() {
        assertThat(GisThematicSources.SERVICE_ORDER.personal()).isTrue();
        assertThat(GisThematicSources.SERVICE_ORDER.publishable()).isFalse();

        assertThat(GisThematicSources.NETWORK_ARC.personal()).isFalse();
        assertThat(GisThematicSources.NETWORK_ARC.publishable()).isTrue();
        assertThat(GisThematicSources.PRECISION.personal()).isFalse();
        assertThat(GisThematicSources.PRECISION.publishable()).isTrue();

        for (GisThematicSources unit : new GisThematicSources[]{
                GisThematicSources.UNIT_CONSUMPTION, GisThematicSources.CONNECTION_STATUS,
                GisThematicSources.METER_AGE, GisThematicSources.UNIT_OVERDUE}) {
            assertThat(unit.personal()).isTrue();
            assertThat(unit.publishable()).isTrue();
            assertThat(unit.unitBased()).isTrue();
        }
        assertThat(GisThematicSources.NETWORK_ARC.unitBased()).isFalse();
        assertThat(GisThematicSources.SERVICE_ORDER.unitBased()).isFalse();
    }

    @Test
    @DisplayName("campos por fonte: categoria, medida e filtro só os declarados")
    void fieldsPerSource() {
        assertThat(GisThematicSources.UNIT_CONSUMPTION.isCategoryField("comparison")).isTrue();
        assertThat(GisThematicSources.UNIT_CONSUMPTION.isCategoryField("system_role")).isFalse();
        assertThat(GisThematicSources.UNIT_CONSUMPTION.isMeasureField("billed_consumption")).isTrue();
        assertThat(GisThematicSources.UNIT_CONSUMPTION.isFilterField("category_code")).isTrue();
        assertThat(GisThematicSources.CONNECTION_STATUS.isCategoryField("system_role")).isTrue();
        assertThat(GisThematicSources.PRECISION.isCategoryField("precision_group")).isTrue();
        assertThat(GisThematicSources.SERVICE_ORDER.isFilterField("serviceTypeCodes")).isTrue();
        assertThat(GisThematicSources.SERVICE_ORDER.isFilterField("protocol_id")).isFalse();
    }
}
