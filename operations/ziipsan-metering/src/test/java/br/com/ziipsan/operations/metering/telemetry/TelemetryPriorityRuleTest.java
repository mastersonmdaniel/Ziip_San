package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A bifurcação {@code meter.telemetry_priority_over_manual} (ED-17,
 * ADR-0038): as DUAS opções têm teste, como o aceite pede.
 */
class TelemetryPriorityRuleTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-10T08:00:00-03:00");
    private static final TelemetryPriorityRule.Candidate MANUAL =
            new TelemetryPriorityRule.Candidate(TelemetryPriorityRule.Source.MANUAL, 1050, AT);
    private static final TelemetryPriorityRule.Candidate TELEMETRY =
            new TelemetryPriorityRule.Candidate(TelemetryPriorityRule.Source.TELEMETRY, 1052, AT);

    @Test
    @DisplayName("parâmetro ligado: a leitura de telemetria tem prioridade sobre a manual")
    void telemetryWinsWhenParameterOn() {
        TelemetryPriorityRule.Candidate chosen = TelemetryPriorityRule.choose(MANUAL, TELEMETRY, true);
        assertThat(chosen).isEqualTo(TELEMETRY);
        assertThat(chosen.source()).isEqualTo(TelemetryPriorityRule.Source.TELEMETRY);
    }

    @Test
    @DisplayName("parâmetro desligado: a leitura manual tem prioridade sobre a de telemetria")
    void manualWinsWhenParameterOff() {
        TelemetryPriorityRule.Candidate chosen = TelemetryPriorityRule.choose(MANUAL, TELEMETRY, false);
        assertThat(chosen).isEqualTo(MANUAL);
        assertThat(chosen.source()).isEqualTo(TelemetryPriorityRule.Source.MANUAL);
    }

    @Test
    @DisplayName("com uma candidata só, vale a que existe, em qualquer posição do parâmetro")
    void singleCandidateWinsEitherWay() {
        assertThat(TelemetryPriorityRule.choose(MANUAL, null, true)).isEqualTo(MANUAL);
        assertThat(TelemetryPriorityRule.choose(null, TELEMETRY, false)).isEqualTo(TELEMETRY);
    }

    @Test
    @DisplayName("sem candidata nenhuma, a escolha é erro explícito")
    void noCandidateIsExplicitError() {
        assertThatThrownBy(() -> TelemetryPriorityRule.choose(null, null, true))
                .isInstanceOf(MeteringInputException.class);
    }
}
