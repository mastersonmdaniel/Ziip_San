package br.com.ziipsan.intelligence.losses.pressure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import br.com.ziipsan.core.parameters.MissingParameterException;

class PressureParametersTest {

    @Test @DisplayName("pressure.min_mca ausente: parametro faltante explicito, nunca valor de fabrica (NBR 12218 nao conferida)")
    void minMissingIsExplicit() {
        assertThatThrownBy(() -> PressureParameters.zoneLimits(null, 60.0))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("pressure.min_mca");
    }

    @Test @DisplayName("pressure.max_mca ausente: parametro faltante explicito")
    void maxMissingIsExplicit() {
        assertThatThrownBy(() -> PressureParameters.zoneLimits(10.0, null))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("pressure.max_mca");
    }

    @Test @DisplayName("limites validos do tenant: dentro/fora da faixa")
    void limitsFromTenant() {
        var limits = PressureParameters.zoneLimits(10.0, 60.0);
        assertThat(limits.isBelow(9.9)).isTrue();
        assertThat(limits.isWithin(10.0)).isTrue();
        assertThat(limits.isWithin(60.0)).isTrue();
        assertThat(limits.isAbove(60.1)).isTrue();
    }

    @Test @DisplayName("limites invalidos recusam: max <= min e minimo negativo")
    void invalidLimitsRefuse() {
        assertThatThrownBy(() -> PressureParameters.zoneLimits(60.0, 60.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PressureParameters.zoneLimits(-1.0, 60.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("leak.repair_phase_targets_hours: tres fases parseadas e consultaveis")
    void repairTargetsParse() {
        var t = PressureParameters.repairPhaseTargets("AWARENESS=8,LOCATION=24,REPAIR=48");
        assertThat(t.targetFor(PressureParameters.RepairPhase.AWARENESS)).isEqualTo(8.0);
        assertThat(t.targetFor(PressureParameters.RepairPhase.LOCATION)).isEqualTo(24.0);
        assertThat(t.targetFor(PressureParameters.RepairPhase.REPAIR)).isEqualTo(48.0);
        assertThat(t.totalHours()).isEqualTo(80.0);
    }

    @Test @DisplayName("metas ausentes ou com fase faltante: parametro faltante nomeado")
    void repairTargetsMissing() {
        assertThatThrownBy(() -> PressureParameters.repairPhaseTargets(null))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("leak.repair_phase_targets_hours");
        assertThatThrownBy(() -> PressureParameters.repairPhaseTargets("AWARENESS=8,REPAIR=48"))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("LOCATION");
        assertThatThrownBy(() -> PressureParameters.repairPhaseTargets("AWARENESS=8,LOCATION=24,REPAIR=0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("leak.survey_cycle_days_by_dmc_class: ciclo por classe do tenant; classe sem ciclo = faltante")
    void surveyCycleByClass() {
        String raw = "URBANO_DENSO=90;RURAL=180";
        assertThat(PressureParameters.surveyCycleDaysFor(raw, "URBANO_DENSO")).isEqualTo(90);
        assertThat(PressureParameters.surveyCycleDaysFor(raw, "RURAL")).isEqualTo(180);
        assertThatThrownBy(() -> PressureParameters.surveyCycleDaysFor(raw, "LITORAL"))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("leak.survey_cycle_days_by_dmc_class");
        assertThatThrownBy(() -> PressureParameters.surveyCycleDaysFor(null, "RURAL"))
                .isInstanceOf(MissingParameterException.class);
    }
}
