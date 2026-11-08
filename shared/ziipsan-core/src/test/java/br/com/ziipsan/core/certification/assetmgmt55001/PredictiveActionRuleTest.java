package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PredictiveActionRuleTest {

    private static List<PredictiveActionRule.Observation> series(double... values) {
        java.util.List<PredictiveActionRule.Observation> out = new java.util.ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        for (double v : values) {
            out.add(new PredictiveActionRule.Observation(d, v));
            d = d.plusDays(1);
        }
        return out;
    }

    @Test
    @DisplayName("série curta demais: INSUFFICIENT_DATA, nunca sinal presumido")
    void insufficientData() {
        var r = PredictiveActionRule.evaluate(Iso55001Fixtures.on(), Iso55001Fixtures.ASSET,
                "vibracao", "mm/s", series(1.0, 99.0),
                new PredictiveActionRule.RuleParameters(5, 2.0, PredictiveActionRule.Direction.ABOVE));
        assertThat(r.verdict()).isEqualTo(PredictiveActionRule.Verdict.INSUFFICIENT_DATA);
        assertThat(r.signal()).isNull();
    }

    @Test
    @DisplayName("afastamento acima de k desvios (parâmetro do tenant) gera sinal; dentro, não")
    void signalByDeviation() {
        var params = new PredictiveActionRule.RuleParameters(4, 2.0,
                PredictiveActionRule.Direction.ABOVE);
        var signal = PredictiveActionRule.evaluate(Iso55001Fixtures.on(), Iso55001Fixtures.ASSET,
                "vibracao", "mm/s", series(10, 11, 9, 10, 10, 30), params);
        assertThat(signal.verdict()).isEqualTo(PredictiveActionRule.Verdict.SIGNAL);
        assertThat(signal.signal().observedValue()).isEqualTo(30.0);
        var quiet = PredictiveActionRule.evaluate(Iso55001Fixtures.on(), Iso55001Fixtures.ASSET,
                "vibracao", "mm/s", series(10, 11, 9, 10, 10, 11), params);
        assertThat(quiet.verdict()).isEqualTo(PredictiveActionRule.Verdict.NO_SIGNAL);
    }

    @Test
    @DisplayName("parâmetros sem fábrica: minSamples < 2 e k <= 0 não constroem a regra")
    void parametersAreTenants() {
        assertThatThrownBy(() -> new PredictiveActionRule.RuleParameters(1, 2.0,
                PredictiveActionRule.Direction.ABOVE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PredictiveActionRule.RuleParameters(3, 0.0,
                PredictiveActionRule.Direction.ABOVE)).isInstanceOf(IllegalArgumentException.class);
    }
}
