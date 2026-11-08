package br.com.ziipsan.commercial.collection.dunning.installment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agenda de parcelas com valores calculados a mao. A ultima parcela
 * absorve o residuo: a soma e sempre o total exato.
 */
class InstallmentCalculatorTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 12, 7);

    private static List<BigDecimal> amounts(InstallmentCalculator.Schedule s) {
        return s.installments().stream().map(InstallmentCalculator.Installment::amount).toList();
    }

    @Test
    @DisplayName("PRICE: 1200 a 1% em 12x — PMT 106,6185 -> total 1279,42 (ultima 106,60)")
    void price() {
        var s = InstallmentCalculator.schedule(new BigDecimal("1200"),
                InstallmentEnums.InterestMethod.PRICE, new BigDecimal("0.01"), 12, FIRST);
        assertThat(s.totalAmount()).isEqualByComparingTo("1279.42");
        assertThat(amounts(s)).hasSize(12);
        assertThat(amounts(s).get(0)).isEqualByComparingTo("106.62");
        assertThat(amounts(s).get(11)).isEqualByComparingTo("106.60");
        assertThat(amounts(s).stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1279.42");
        assertThat(s.installments().get(1).dueDate()).isEqualTo(LocalDate.of(2027, 1, 7));
    }

    @Test
    @DisplayName("COMPOUND: 1000 a 2% em 4x — total 1000*1,02^4 = 1082,43 (ultima 270,60)")
    void compound() {
        var s = InstallmentCalculator.schedule(new BigDecimal("1000"),
                InstallmentEnums.InterestMethod.COMPOUND, new BigDecimal("0.02"), 4, FIRST);
        assertThat(s.totalAmount()).isEqualByComparingTo("1082.43");
        assertThat(amounts(s)).containsExactly(
                new BigDecimal("270.61"), new BigDecimal("270.61"),
                new BigDecimal("270.61"), new BigDecimal("270.60"));
    }

    @Test
    @DisplayName("NONE: 1000 em 3x sem juros — 333,33 / 333,33 / 333,34")
    void none() {
        var s = InstallmentCalculator.schedule(new BigDecimal("1000"),
                InstallmentEnums.InterestMethod.NONE, BigDecimal.ZERO, 3, FIRST);
        assertThat(s.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(amounts(s)).containsExactly(
                new BigDecimal("333.33"), new BigDecimal("333.33"), new BigDecimal("333.34"));
    }

    @Test
    @DisplayName("NONE com taxa positiva e recusado; PRICE com taxa zero divide igual")
    void noneWithRateRefusedAndPriceZeroRate() {
        assertThatThrownBy(() -> InstallmentCalculator.schedule(new BigDecimal("1000"),
                InstallmentEnums.InterestMethod.NONE, new BigDecimal("0.01"), 3, FIRST))
                .isInstanceOf(IllegalArgumentException.class);
        var s = InstallmentCalculator.schedule(new BigDecimal("900"),
                InstallmentEnums.InterestMethod.PRICE, BigDecimal.ZERO, 3, FIRST);
        assertThat(amounts(s)).containsExactly(
                new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00"));
    }
}
