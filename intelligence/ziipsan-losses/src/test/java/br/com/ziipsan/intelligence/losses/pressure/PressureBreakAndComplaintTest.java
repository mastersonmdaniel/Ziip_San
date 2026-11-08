package br.com.ziipsan.intelligence.losses.pressure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class PressureBreakAndComplaintTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 3, 0);

    static PressureBreakCorrelation.ArcObservation arc(String id, double mean, int breaks, Double km) {
        return new PressureBreakCorrelation.ArcObservation(id, mean, mean + 10, breaks, km);
    }
    static PressureSeriesPort.PressureSample s(int h, double mca) {
        return new PressureSeriesPort.PressureSample(T0.plusHours(h), mca);
    }

    @Test @DisplayName("pressao x rompimentos por arco: relacao linear perfeita da taxa -> Pearson 1,0 e ranking pela taxa")
    void perfectCorrelation() {
        // taxa por km = 0,1 x pressao media (10->1, 20->2, 30->3, 40->4 romp/km)
        var r = PressureBreakCorrelation.correlate(List.of(
                arc("A1", 10, 2, 2.0), arc("A2", 20, 4, 2.0),
                arc("A3", 30, 6, 2.0), arc("A4", 40, 8, 2.0)));
        assertThat(r.pearsonMeanPressureVsBreaksPerKm()).isPresent();
        assertThat(r.pearsonMeanPressureVsBreaksPerKm().getAsDouble()).isCloseTo(1.0, within(1e-9));
        assertThat(r.totalBreaks()).isEqualTo(20);
        assertThat(r.rankedByBreakRatePerKm()).extracting(PressureBreakCorrelation.ArcObservation::arcId)
                .containsExactly("A4", "A3", "A2", "A1");
    }

    @Test @DisplayName("sem variancia ou sem extensao: correlacao ausente, nunca inventada")
    void correlationAbsentWithoutBasis() {
        var flat = PressureBreakCorrelation.correlate(List.of(
                arc("A1", 30, 2, 2.0), arc("A2", 30, 5, 2.0)));
        assertThat(flat.pearsonMeanPressureVsBreaksPerKm()).isEmpty();
        var noLength = PressureBreakCorrelation.correlate(List.of(
                arc("A1", 10, 2, null), arc("A2", 40, 8, null)));
        assertThat(noLength.arcsWithLength()).isZero();
        assertThat(noLength.pearsonMeanPressureVsBreaksPerKm()).isEmpty();
        assertThat(noLength.rankedByBreakRatePerKm()).isEmpty();
    }

    @Test @DisplayName("reclamacao de pouca agua (OPE-04): corroborada abaixo do minimo, nao corroborada dentro, NO_DATA sem medicao")
    void complaintOutcomes() {
        var limits = PressureParameters.zoneLimits(10.0, 60.0);
        var complaint = new LowWaterComplaintAssessment.ComplaintRef(
                "SAC-2026-001", UUID.randomUUID(), "PC-1", T0);
        var low = LowWaterComplaintAssessment.assess(complaint, List.of(s(0, 22.0), s(1, 7.5)), limits);
        assertThat(low.outcome()).isEqualTo(LowWaterComplaintAssessment.Outcome.CORROBORATED_LOW_PRESSURE);
        assertThat(low.lowestObservedMcaOrNull()).isEqualTo(7.5);
        assertThat(low.lowSamples()).isEqualTo(1);
        var ok = LowWaterComplaintAssessment.assess(complaint, List.of(s(0, 22.0), s(1, 18.0)), limits);
        assertThat(ok.outcome()).isEqualTo(LowWaterComplaintAssessment.Outcome.NOT_CORROBORATED);
        var none = LowWaterComplaintAssessment.assess(complaint, List.of(), limits);
        assertThat(none.outcome()).isEqualTo(LowWaterComplaintAssessment.Outcome.NO_DATA);
        assertThat(none.lowestObservedMcaOrNull()).isNull();
    }
}
