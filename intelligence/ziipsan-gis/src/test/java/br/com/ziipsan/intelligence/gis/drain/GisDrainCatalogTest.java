package br.com.ziipsan.intelligence.gis.drain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catálogo da GIS-13: 20 tipos — 19 do §4.4 + STORM_BASIN da
 * V13_2/§3.6.5 (decisão D2 da revisão) — papéis, classes e séries
 * de referência.
 */
class GisDrainCatalogTest {

    @Test
    void vinteTipos19DoParagrafo44MaisStormBasinDaV13_2() {
        assertThat(GisDrainCatalog.TYPES).hasSize(20);
        assertThat(GisDrainCatalog.TYPES.stream().map(GisDrainCatalog.DrainType::code))
                .containsExactly(
                        "STORM_PIPE", "BOX_CULVERT", "OPEN_CHANNEL", "CULVERT",
                        "INLET_LEAD", "GUTTER", "VALLEY_GUTTER", "DITCH",
                        "INLET", "GRATE_INLET", "STORM_MANHOLE", "STORM_JUNCTION_BOX",
                        "DROP_SHAFT", "ENERGY_DISSIPATOR", "OUTFALL",
                        "HEADWALL", "DETENTION_BASIN", "RETENTION_BASIN",
                        "STORM_BASIN", "STORM_SUBCATCHMENT");
    }

    @Test
    void papeisETabelasPorTipo() {
        assertThat(GisDrainCatalog.requireType("STORM_PIPE").featureTable()).isEqualTo("gis_arc");
        assertThat(GisDrainCatalog.requireType("INLET").featureTable()).isEqualTo("gis_node");
        assertThat(GisDrainCatalog.requireType("DETENTION_BASIN").featureTable())
                .isEqualTo("gis_structure");
        assertThat(GisDrainCatalog.requireType("STORM_SUBCATCHMENT").featureTable())
                .isEqualTo("gis_zone");
        assertThat(GisDrainCatalog.requireType("STORM_SUBCATCHMENT").zoneType())
                .isEqualTo("STORM_SUBCATCHMENT");
        assertThat(GisDrainCatalog.requireType("STORM_BASIN").zoneType())
                .isEqualTo("STORM_BASIN");
        assertThat(GisDrainCatalog.arcTypes()).hasSize(8);
    }

    @Test
    void factoryCodesUnicosComPrefixoGis13() {
        Set<String> codes = new HashSet<>();
        for (GisDrainCatalog.DrainType t : GisDrainCatalog.TYPES) {
            assertThat(t.factoryCode()).startsWith("gis13:");
            assertThat(codes.add(t.factoryCode())).isTrue();
        }
    }

    @Test
    void seriesDnDeReferencia() {
        assertThat(GisDrainCatalog.isReferenceDn("STORM_PIPE", 300)).isTrue();
        assertThat(GisDrainCatalog.isReferenceDn("STORM_PIPE", 1500)).isTrue();
        assertThat(GisDrainCatalog.isReferenceDn("STORM_PIPE", 250)).isFalse();
        assertThat(GisDrainCatalog.isReferenceDn("INLET_LEAD", 400)).isTrue();
        assertThat(GisDrainCatalog.isReferenceDn("INLET_LEAD", 500)).isFalse();
    }

    @Test
    void manningDeReferenciaSoOndeOFonteDa() {
        assertThat(GisDrainCatalog.MATERIAL_MANNING_REFERENCE)
                .containsEntry("CONCRETE", 0.013)
                .containsEntry("HDPE_STRUCTURED", 0.010)
                .doesNotContainKey("CORRUGATED_STEEL");
        assertThat(GisDrainCatalog.LINING_MANNING_REFERENCE)
                .containsEntry("EARTH", 0.025)
                .doesNotContainKey("GABION");
    }

    @Test
    void tipoForaDoCatalogoRecusado() {
        assertThatThrownBy(() -> GisDrainCatalog.requireType("STORM_X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("§4.4");
    }
}
