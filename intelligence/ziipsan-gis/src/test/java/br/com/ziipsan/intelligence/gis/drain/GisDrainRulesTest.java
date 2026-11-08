package br.com.ziipsan.intelligence.gis.drain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static br.com.ziipsan.intelligence.gis.drain.GisDrainRules.Severity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras §4.4/§5.4: atributos, declividade, sentido, sub-bacia SWMM, inspeção. */
class GisDrainRulesTest {

    private static GisDrainCatalog.DrainType type(String code) {
        return GisDrainCatalog.requireType(code);
    }

    @Test
    void declividadeCasoConhecido() {
        // queda de 1,00 m em 100 m: declividade 0,01 (a coluna slope da
        // V13_2 guardaria -0,01: sinal trocado por construcao).
        assertThat(GisDrainRules.declivity(10.0, 9.0, 100.0)).isEqualTo(0.01);
        assertThat(GisDrainRules.checkSlope(10.0, 9.0, 100.0, null)).isEmpty();
    }

    @Test
    void declividadeAdversaEErro() {
        List<GisDrainRules.DrainIssue> issues =
                GisDrainRules.checkSlope(9.0, 10.0, 100.0, null);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).ruleCode()).isEqualTo("STORM_SLOPE_ADVERSE");
        assertThat(issues.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void declividadeNulaEErro() {
        List<GisDrainRules.DrainIssue> issues =
                GisDrainRules.checkSlope(10.0, 10.0, 100.0, null);
        assertThat(issues.get(0).ruleCode()).isEqualTo("STORM_SLOPE_FLAT");
        assertThat(issues.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void abaixoDaMinimaSoComParametroDoTenant() {
        // 0,002 < 0,005 do tenant: aviso. Sem parametro: nenhum aviso
        // (nenhum piso inventado).
        List<GisDrainRules.DrainIssue> issues =
                GisDrainRules.checkSlope(10.0, 9.8, 100.0, 0.005);
        assertThat(issues.get(0).ruleCode()).isEqualTo("STORM_SLOPE_BELOW_MIN");
        assertThat(issues.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(GisDrainRules.checkSlope(10.0, 9.8, 100.0, null)).isEmpty();
    }

    @Test
    void sentidoContrarioAsCotasEErro() {
        assertThat(GisDrainRules.checkFlowDirection("WITH_GEOMETRY", 9.0, 10.0))
                .extracting(GisDrainRules.DrainIssue::ruleCode)
                .containsExactly("STORM_FLOW_AGAINST_INVERTS");
        assertThat(GisDrainRules.checkFlowDirection("WITH_GEOMETRY", 10.0, 9.0)).isEmpty();
        assertThat(GisDrainRules.checkFlowDirection("AGAINST_GEOMETRY", 10.0, 9.0))
                .extracting(GisDrainRules.DrainIssue::ruleCode)
                .containsExactly("STORM_FLOW_AGAINST_INVERTS");
        assertThat(GisDrainRules.checkFlowDirection("UNKNOWN", 9.0, 10.0)).isEmpty();
    }

    @Test
    void atributosDeTipoComListasDoParagrafo44() {
        assertThat(GisDrainRules.ensureFeatureAttributes(type("INLET"),
                Map.of("inlet_kind", "DOUBLE"))).isEmpty();
        assertThatThrownBy(() -> GisDrainRules.ensureFeatureAttributes(type("INLET"),
                Map.of("inlet_kind", "QUADRUPLE")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisDrainRules.ensureFeatureAttributes(type("GRATE_INLET"),
                Map.of("load_class", "Z900")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisDrainRules.ensureFeatureAttributes(type("BOX_CULVERT"),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cell_count");
    }

    @Test
    void dnForaDaSerieEAvisoNuncaRecusa() {
        List<GisDrainRules.DrainIssue> warnings = GisDrainRules.ensureFeatureAttributes(
                type("STORM_PIPE"), Map.of("dn_mm", 250));
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).ruleCode()).isEqualTo("STORM_DN_OUTSIDE_REFERENCE");
        assertThat(warnings.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(GisDrainRules.ensureFeatureAttributes(
                type("STORM_PIPE"), Map.of("dn_mm", 600))).isEmpty();
    }

    @Test
    void subBaciaSwmmHortonCompletaEIncompleta() {
        Map<String, Object> ok = Map.of(
                "area_ha", 12.5, "impervious_pct", 65.0, "slope_pct", 1.2,
                "width_m", 350.0, "infiltration_model", "HORTON", "outlet_ref", "PV-01",
                "infiltration_max_rate_mm_h", 76.0, "infiltration_min_rate_mm_h", 3.0,
                "infiltration_decay_per_h", 4.0);
        assertThat(GisDrainRules.ensureFeatureAttributes(type("STORM_SUBCATCHMENT"), ok))
                .isEmpty();
        Map<String, Object> semDecaimento = Map.of(
                "area_ha", 12.5, "impervious_pct", 65.0, "slope_pct", 1.2,
                "width_m", 350.0, "infiltration_model", "HORTON", "outlet_ref", "PV-01",
                "infiltration_max_rate_mm_h", 76.0, "infiltration_min_rate_mm_h", 3.0);
        assertThatThrownBy(() -> GisDrainRules.ensureFeatureAttributes(
                type("STORM_SUBCATCHMENT"), semDecaimento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("infiltration_decay_per_h");
    }

    @Test
    void subBaciaSwmmCurveNumberEGreenAmpt() {
        Map<String, Object> cn = Map.of(
                "area_ha", 3.0, "impervious_pct", 40.0, "slope_pct", 0.8,
                "width_m", 120.0, "infiltration_model", "CURVE_NUMBER",
                "outlet_ref", "SUB-02", "curve_number", 82.0);
        assertThat(GisDrainRules.ensureFeatureAttributes(type("STORM_SUBCATCHMENT"), cn))
                .isEmpty();
        Map<String, Object> ga = Map.of(
                "area_ha", 3.0, "impervious_pct", 40.0, "slope_pct", 0.8,
                "width_m", 120.0, "infiltration_model", "GREEN_AMPT",
                "outlet_ref", "SUB-02", "suction_head_mm", 200.0,
                "hydraulic_conductivity_mm_h", 12.0, "initial_deficit", 0.3);
        assertThat(GisDrainRules.ensureFeatureAttributes(type("STORM_SUBCATCHMENT"), ga))
                .isEmpty();
        assertThatThrownBy(() -> GisDrainRules.ensureSubcatchmentAttributes(Map.of(
                "area_ha", -1.0, "impervious_pct", 40.0, "slope_pct", 0.8,
                "width_m", 120.0, "infiltration_model", "HORTON", "outlet_ref", "X",
                "infiltration_max_rate_mm_h", 1.0, "infiltration_min_rate_mm_h", 1.0,
                "infiltration_decay_per_h", 1.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inspecaoComVideoExigeReferenciaDaEd56() {
        GisDrainRules.ensureInspection(LocalDate.of(2026, 10, 5), "VISUAL",
                "FAIR", 30.0, null);
        GisDrainRules.ensureInspection(LocalDate.of(2026, 10, 5), "CCTV_EXTERNAL_REF",
                "POOR", 80.0, "ensaio-ed56/trecho-12.mp4");
        assertThatThrownBy(() -> GisDrainRules.ensureInspection(
                LocalDate.of(2026, 10, 5), "CCTV_EXTERNAL_REF", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisDrainRules.ensureInspection(
                LocalDate.of(2026, 10, 5), "VISUAL", null, 140.0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0..100");
        assertThatThrownBy(() -> GisDrainRules.ensureInspection(
                null, "VISUAL", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
