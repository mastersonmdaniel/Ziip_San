package br.com.ziipsan.integration.sinisa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ILI (P4.2, caso da v1). Valores conferidos a mao:
 * Lm = 100 km, Nc = 5.000 ligacoes, Lp = 0 km, P = 50 mca.
 * UARL = (18 x 100 + 0,8 x 5.000 + 25 x 0) x 50 = (1.800 + 4.000) x 50 = 290.000 L/dia.
 * CARL de 105.850 m3/ano = 105.850.000 L / 365 = 290.000 L/dia, logo ILI = 1.
 */
class IliCalculatorTest {

    private static final BigDecimal LM = new BigDecimal("100");
    private static final long NC = 5_000L;
    private static final BigDecimal LP = BigDecimal.ZERO;
    private static final BigDecimal P = new BigDecimal("50");

    @Test
    @DisplayName("UARL pela formula IWA/Lambert: (18x100 + 0,8x5000 + 25x0) x 50 = 290.000 L/dia")
    void uarlFormula() {
        BigDecimal uarl = IliCalculator.unavoidableAnnualRealLossesLitresPerDay(LM, NC, LP, P);

        assertThat(uarl).isEqualByComparingTo("290000");
    }

    @Test
    @DisplayName("caso da v1: CARL igual a UARL em L/dia da ILI exatamente 1")
    void iliUmQuandoCarlIgualUarl() {
        BigDecimal ili = IliCalculator.infrastructureLeakageIndex(new BigDecimal("105850"), LM, NC, LP, P);

        assertThat(ili).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("CARL dobrado da ILI exatamente 2")
    void iliDoisComCarlDobrado() {
        BigDecimal ili = IliCalculator.infrastructureLeakageIndex(new BigDecimal("211700"), LM, NC, LP, P);

        assertThat(ili).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("ILI compoe com o balanco: CARL do WaterBalance alimenta o IliCalculator")
    void iliCompoeComBalanco() {
        WaterBalance balance = WaterBalance.calculate(
                new BigDecimal("211700"), new BigDecimal("105850"), BigDecimal.ZERO, BigDecimal.ZERO);
        // entrada 211.700, faturado 105.850, sem nao faturado autorizado nem aparentes:
        // perdas reais = 105.850 m3 = CARL do caso ILI = 1
        assertThat(balance.carlM3()).isEqualByComparingTo("105850");
        BigDecimal ili = IliCalculator.infrastructureLeakageIndex(balance.carlM3(), LM, NC, LP, P);
        assertThat(ili).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("UARL nula (rede, ligacoes, ramais e pressao zerados) torna o ILI indefinido e e recusada")
    void uarlNulaRecusada() {
        assertThatThrownBy(() -> IliCalculator.infrastructureLeakageIndex(
                new BigDecimal("1000"), BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UARL nula");
    }

    @Test
    @DisplayName("insumo negativo e recusado")
    void insumoNegativoRecusado() {
        assertThatThrownBy(() -> IliCalculator.unavoidableAnnualRealLossesLitresPerDay(
                new BigDecimal("-1"), NC, LP, P))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IliCalculator.infrastructureLeakageIndex(
                new BigDecimal("-5"), LM, NC, LP, P))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
