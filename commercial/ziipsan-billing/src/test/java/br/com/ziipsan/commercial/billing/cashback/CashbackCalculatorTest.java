package br.com.ziipsan.commercial.billing.cashback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cálculo do cashback (P2.34): 100% da CBS e 20% do IBS (LC 214/2025),
 * por vigência — CBS desde 01/2027, IBS desde 01/2029. Todos os valores
 * esperados foram calculados à mão e fixados como literais:
 * 20% de 10,00 = 2,00; 20% de 33,33 = 6,666 → 6,67 (HALF_UP).
 */
class CashbackCalculatorTest {

    @Test
    @DisplayName("constantes normativas: 100% CBS, 20% IBS, vigências 01/2027 e 01/2029")
    void normativeFacts() {
        assertThat(CashbackVigencia.CBS_RETURN_PCT).isEqualByComparingTo("100");
        assertThat(CashbackVigencia.IBS_RETURN_PCT).isEqualByComparingTo("20");
        assertThat(CashbackVigencia.CBS_FROM).isEqualTo(YearMonth.of(2027, 1));
        assertThat(CashbackVigencia.IBS_FROM).isEqualTo(YearMonth.of(2029, 1));
    }

    @Test
    @DisplayName("antes de 01/2027: nada é devolvido (CBS 50,00 e IBS 50,00 → 0,00)")
    void beforeAnyVigencia() {
        var c = CashbackCalculator.calcular(YearMonth.of(2026, 12),
                new BigDecimal("50.00"), new BigDecimal("50.00"));
        assertThat(c.cbsDevolvida()).isEqualByComparingTo("0.00");
        assertThat(c.ibsDevolvida()).isEqualByComparingTo("0.00");
        assertThat(c.totalDevolvido()).isEqualByComparingTo("0.00");
        assertThat(c.temDevolucao()).isFalse();
    }

    @Test
    @DisplayName("fronteira CBS: 01/2027 devolve 100% da CBS e nada do IBS (50,00/50,00 → 50,00 + 0,00)")
    void cbsBoundary() {
        var c = CashbackCalculator.calcular(YearMonth.of(2027, 1),
                new BigDecimal("50.00"), new BigDecimal("50.00"));
        assertThat(c.cbsDevolvida()).isEqualByComparingTo("50.00");
        assertThat(c.ibsDevolvida()).isEqualByComparingTo("0.00");
        assertThat(c.totalDevolvido()).isEqualByComparingTo("50.00");
        assertThat(c.cbsNaVigencia()).isTrue();
        assertThat(c.ibsNaVigencia()).isFalse();
    }

    @Test
    @DisplayName("entre vigências (06/2028): só CBS (30,00/10,00 → 30,00 + 0,00)")
    void betweenVigencias() {
        var c = CashbackCalculator.calcular(YearMonth.of(2028, 6),
                new BigDecimal("30.00"), new BigDecimal("10.00"));
        assertThat(c.totalDevolvido()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("fronteira IBS, véspera: 12/2028 ainda é só CBS (30,00/10,00 → 30,00 + 0,00)")
    void ibsBoundaryEve() {
        // Acrescentado na revisão cruzada: o relatório citava 12/2028, mas nenhum teste o exercitava.
        var c = CashbackCalculator.calcular(YearMonth.of(2028, 12),
                new BigDecimal("30.00"), new BigDecimal("10.00"));
        assertThat(c.cbsDevolvida()).isEqualByComparingTo("30.00");
        assertThat(c.ibsDevolvida()).isEqualByComparingTo("0.00");
        assertThat(c.totalDevolvido()).isEqualByComparingTo("30.00");
        assertThat(c.cbsNaVigencia()).isTrue();
        assertThat(c.ibsNaVigencia()).isFalse();
    }

    @Test
    @DisplayName("fronteira IBS: 01/2029 devolve CBS integral + 20% do IBS (30,00/10,00 → 30,00 + 2,00 = 32,00)")
    void ibsBoundary() {
        var c = CashbackCalculator.calcular(YearMonth.of(2029, 1),
                new BigDecimal("30.00"), new BigDecimal("10.00"));
        assertThat(c.cbsDevolvida()).isEqualByComparingTo("30.00");
        assertThat(c.ibsDevolvida()).isEqualByComparingTo("2.00");
        assertThat(c.totalDevolvido()).isEqualByComparingTo("32.00");
    }

    @Test
    @DisplayName("arredondamento do IBS: 20% de 33,33 = 6,67 (HALF_UP), calculado à mão")
    void ibsRounding() {
        var c = CashbackCalculator.calcular(YearMonth.of(2029, 3),
                new BigDecimal("0.00"), new BigDecimal("33.33"));
        assertThat(c.ibsDevolvida()).isEqualByComparingTo("6.67");
        assertThat(c.totalDevolvido()).isEqualByComparingTo("6.67");
    }

    @Test
    @DisplayName("valor ausente, negativo ou com 3 casas é erro explícito, nunca zero presumido")
    void invalidAmountsAreErrors() {
        assertThatThrownBy(() -> CashbackCalculator.calcular(YearMonth.of(2029, 1), null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashbackCalculator.calcular(YearMonth.of(2029, 1),
                new BigDecimal("-1.00"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashbackCalculator.calcular(YearMonth.of(2029, 1),
                new BigDecimal("1.001"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
