package br.com.ziipsan.core.stationops;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parametros: ausencia = erro explicito; o tenant so REDUZ o intervalo
 * contra o teto normativo entregue (nenhum teto numerico de fabrica,
 * exceto as 2 h COM FONTE da turbidez).
 */
class StationOpsParametersTest {

    @Test
    void shiftPatternMissingIsExplicitError() {
        assertThatThrownBy(() -> StationOpsParameters.shiftPattern(null))
                .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> StationOpsParameters.shiftPattern(List.of()))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    void shiftPatternRejectsDuplicateCodesAndFindsShift() {
        var dup = List.of(
                new StationOpsParameters.ShiftDefinition("DIA", java.time.LocalTime.of(6, 0), java.time.LocalTime.of(18, 0)),
                new StationOpsParameters.ShiftDefinition("DIA", java.time.LocalTime.of(7, 0), java.time.LocalTime.of(19, 0)));
        assertThatThrownBy(() -> StationOpsParameters.shiftPattern(dup))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StationOpsParameters.requireShift(TestSupport.pattern(), "NOITE").crossesMidnight()).isTrue();
        assertThatThrownBy(() -> StationOpsParameters.requireShift(TestSupport.pattern(), "MADRUGADA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundIntervalTenantMayOnlyReduceAgainstCeiling() {
        // Teto sintetico de fixture (240): NAO e a norma — a norma real e
        // conferida na implantacao [SEM FONTE como constante].
        Map<String, Integer> tenant = Map.of("ETA-01", 120);
        Map<String, Integer> ceiling = Map.of("ETA-01", 240);
        assertThat(StationOpsParameters.roundIntervalMinutes("ETA-01", tenant, ceiling)).isEqualTo(120);
        assertThat(StationOpsParameters.roundIntervalMinutes("ETA-01", Map.of("ETA-01", 240), ceiling))
                .isEqualTo(240);
        assertThatThrownBy(() -> StationOpsParameters.roundIntervalMinutes(
                "ETA-01", Map.of("ETA-01", 300), ceiling))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("so reduz");
    }

    @Test
    void roundIntervalMissingValueOrCeilingIsExplicitError() {
        assertThatThrownBy(() -> StationOpsParameters.roundIntervalMinutes(
                "ETA-01", Map.of(), Map.of("ETA-01", 240)))
                .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> StationOpsParameters.roundIntervalMinutes(
                "ETA-01", Map.of("ETA-01", 60), Map.of()))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    void turbidityCeilingIsTheSourcedTwoHours() {
        // Portaria GM/MS 888/2021, art. 28, §3o, conforme o aceite (OPE-02).
        assertThat(StationOpsParameters.TURBIDITY_MAX_INTERVAL_MINUTES).isEqualTo(120);
    }
}
