package br.com.ziipsan.intelligence.losses.dmc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

class NightFlowAnalyzerTest {
    static NightFlowAnalyzer.HourlyFlow h(int day, int hour, double flow) {
        return new NightFlowAnalyzer.HourlyFlow(LocalDateTime.of(2026, 3, day, hour, 0), flow);
    }

    @Test @DisplayName("minima noturna e liquida com entradas reais: minimo 9000 L/h - legitimo 1,7x1000 = 7300 L/h")
    void netNightFlowReal() {
        LossParameters.NightWindow w = LossParameters.nightWindow(null);
        var samples = List.of(h(10, 1, 20000), h(10, 2, 12000), h(10, 3, 9000),
                h(10, 4, 15000), h(10, 5, 25000));
        NightFlowAnalyzer.NightFlowResult r = NightFlowAnalyzer.analyze(samples, w, 1.7, 1000);
        assertThat(r.minimumNightFlowLPerH()).isEqualTo(9000.0);
        assertThat(r.legitNightUseTotalLPerH()).isEqualTo(1700.0);
        assertThat(r.netNightFlowLPerH()).isEqualTo(7300.0);
        assertThat(r.samplesInWindow()).isEqualTo(2);
        assertThat(r.legitExceedsMinimum()).isFalse();
        assertThat(r.netNightVolumeM3()).isCloseTo(14.6, within(1e-9));
    }

    @Test @DisplayName("sem amostra na janela: recusa (minima nao se presume)")
    void emptyWindowRefuses() {
        LossParameters.NightWindow w = LossParameters.nightWindow(null);
        assertThatThrownBy(() -> NightFlowAnalyzer.analyze(List.of(h(10, 10, 5000)), w, 1.0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("liquida negativa e exposta, nunca truncada para zero")
    void negativeNetExposed() {
        LossParameters.NightWindow w = LossParameters.nightWindow(null);
        var r = NightFlowAnalyzer.analyze(List.of(h(10, 2, 1000), h(10, 3, 1100)), w, 5.0, 1000);
        assertThat(r.netNightFlowLPerH()).isEqualTo(-4000.0);
        assertThat(r.legitExceedsMinimum()).isTrue();
    }
}
