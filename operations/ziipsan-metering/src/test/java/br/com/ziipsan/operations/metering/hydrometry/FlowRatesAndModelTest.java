package br.com.ziipsan.operations.metering.hydrometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/** Vazões da Portaria 155/2022: Q2/Q1=1,6; Q4/Q3=1,25; R do catálogo (ED-14). */
class FlowRatesAndModelTest {
    @Test @DisplayName("Q1, Q2 e Q4 derivam de Q3 e R pelas razões da portaria")
    void derivesFlows() {
        FlowRates f = FlowRates.derive(new BigDecimal("2.5"), 160);
        assertThat(f.q1()).isEqualByComparingTo("0.015625");
        assertThat(f.q2()).isEqualByComparingTo("0.025");
        assertThat(f.q4()).isEqualByComparingTo("3.125");
        assertThat(f.matches(new BigDecimal("0.015625"), new BigDecimal("0.025"), new BigDecimal("3.125"))).isTrue();
        assertThat(f.matches(new BigDecimal("0.02"), new BigDecimal("0.025"), new BigDecimal("3.125"))).isFalse();
    }
    @Test @DisplayName("R fora da faixa do catálogo e Q3 inválido são recusados")
    void rejectsBadR() {
        assertThatThrownBy(() -> FlowRates.derive(new BigDecimal("2.5"), 9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlowRates.derive(new BigDecimal("2.5"), 801)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlowRates.derive(BigDecimal.ZERO, 100)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("modelo exige portaria, DN, classe 1 ou 2, posição e regime — sem texto livre")
    void modelValidation() {
        var ok = new MeterModelRule.ModelFacts("Portaria Inmetro 155/2022", new BigDecimal("20"),
                new BigDecimal("2.5"), 160, 2, HydrometryEnums.InstallationPosition.HORIZONTAL, HydrometryEnums.Regime.INITIAL_VERIFICATION);
        assertThat(MeterModelRule.validate(ok).q3()).isEqualByComparingTo("2.5");
        var badClass = new MeterModelRule.ModelFacts("P", new BigDecimal("20"), new BigDecimal("2.5"), 160, 3,
                HydrometryEnums.InstallationPosition.ANY, HydrometryEnums.Regime.INITIAL_VERIFICATION);
        assertThatThrownBy(() -> MeterModelRule.validate(badClass)).isInstanceOf(IllegalArgumentException.class);
        var noOrdinance = new MeterModelRule.ModelFacts(" ", new BigDecimal("20"), new BigDecimal("2.5"), 160, 1,
                HydrometryEnums.InstallationPosition.ANY, HydrometryEnums.Regime.INITIAL_VERIFICATION);
        assertThatThrownBy(() -> MeterModelRule.validate(noOrdinance)).isInstanceOf(IllegalArgumentException.class);
    }
}
