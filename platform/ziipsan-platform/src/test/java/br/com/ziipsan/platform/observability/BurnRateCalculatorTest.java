package br.com.ziipsan.platform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BurnRateCalculatorTest {

    private static final SloDefinition SLO_999 = new SloDefinition("api.availability", 99.9, 30);

    @Test
    @DisplayName("burn rate calculado à mão: 250 ruins em 100.000 com SLO 99,9% → burn 2,5 e 250% do orçamento")
    void handCalculatedBurnRate() {
        // taxa observada = 250/100.000 = 0,0025; permitida = 0,001; burn = 2,5
        // orçamento = 0,001 × 100.000 = 100 eventos; consumido = 250/100 = 2,5 (250%)
        BurnRateCalculator.Result r = BurnRateCalculator.calculate(SLO_999, 100_000, 250);
        assertThat(r.observedErrorRate()).isCloseTo(0.0025, within(1e-12));
        assertThat(r.burnRate()).isCloseTo(2.5, within(1e-12));
        assertThat(r.errorBudgetEvents()).isCloseTo(100.0, within(1e-9));
        assertThat(r.consumedFraction()).isCloseTo(2.5, within(1e-12));
        assertThat(r.remainingFraction()).isCloseTo(-1.5, within(1e-12));
    }

    @Test
    @DisplayName("no ritmo exato do SLO: 10 ruins em 10.000 → burn 1,0 e orçamento 100% consumido")
    void burnExactlyOne() {
        BurnRateCalculator.Result r = BurnRateCalculator.calculate(SLO_999, 10_000, 10);
        assertThat(r.burnRate()).isCloseTo(1.0, within(1e-12));
        assertThat(r.consumedFraction()).isCloseTo(1.0, within(1e-12));
        assertThat(r.remainingFraction()).isCloseTo(0.0, within(1e-12));
    }

    @Test
    @DisplayName("esgotamento: 600 ruins em 1.000.000 em 10 dias → restam 400 de orçamento, 60/dia → 6,666... dias")
    void daysToExhaustionHandCalculated() {
        // orçamento = 0,001 × 1.000.000 = 1.000; restantes = 400; ritmo = 60/dia; 400/60 = 6,666...
        double days = BurnRateCalculator.daysToExhaustion(SLO_999, 1_000_000, 600, 10.0);
        assertThat(days).isCloseTo(400.0 / 60.0, within(1e-9));
    }

    @Test
    @DisplayName("janela sem eventos é erro explícito, não burn zero")
    void emptyWindowIsError() {
        assertThatThrownBy(() -> BurnRateCalculator.calculate(SLO_999, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sem eventos");
    }

    @Test
    @DisplayName("tiers multi-janela: 14,4 pagina rápido; 6 pagina; 3 ticket rápido; 1 ticket lento")
    void tiers() {
        assertThat(BurnRateCalculator.tierFor(14.4, 2.0)).isEqualTo(BurnRateCalculator.AlertTier.PAGE_FAST);
        assertThat(BurnRateCalculator.tierFor(6.0, 1.0)).isEqualTo(BurnRateCalculator.AlertTier.PAGE_SLOW);
        assertThat(BurnRateCalculator.tierFor(2.0, 3.0)).isEqualTo(BurnRateCalculator.AlertTier.TICKET_FAST);
        assertThat(BurnRateCalculator.tierFor(0.5, 1.0)).isEqualTo(BurnRateCalculator.AlertTier.TICKET_SLOW);
        assertThat(BurnRateCalculator.tierFor(0.5, 0.5)).isEqualTo(BurnRateCalculator.AlertTier.NONE);
    }
}
