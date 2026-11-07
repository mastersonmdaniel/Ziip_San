package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2.20: limite so contra o usuario; a favor nunca limitada; diferenca sem multa/juros. */
class RectificationLimitRuleTest {

    private static MaintenanceParameters params(String limitsJson) {
        return MaintenanceParameters.fromValues(
                Map.of(MaintenanceParameters.RECTIFICATION_LIMITS_AGAINST_USER, limitsJson));
    }

    private static BigDecimal m(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("direcao: 100,00 -> 120,00 contra; -> 80,00 a favor; -> 100,00 neutra")
    void direction() {
        assertThat(RectificationLimitRule.direction(m("100.00"), m("120.00")))
                .isEqualTo(RectificationLimitRule.Direction.AGAINST_USER);
        assertThat(RectificationLimitRule.direction(m("100.00"), m("80.00")))
                .isEqualTo(RectificationLimitRule.Direction.IN_FAVOR_OF_USER);
        assertThat(RectificationLimitRule.direction(m("100.00"), m("100.00")))
                .isEqualTo(RectificationLimitRule.Direction.NEUTRAL);
    }

    @Test
    @DisplayName("contra o usuario com teto 2 e 2 ja feitas: recusa; com 1 feita: passa")
    void againstUserCapped() {
        MaintenanceParameters p = params("{\"maxPerUnitAndYear\": 2}");
        assertThatThrownBy(() -> RectificationLimitRule.check(p, m("100.00"), m("120.00"), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.rectification_limits_against_user");
        assertThatCode(() -> RectificationLimitRule.check(p, m("100.00"), m("120.00"), 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a favor do usuario NUNCA limitada: teto 0 e 99 feitas, 100,00 -> 80,00 passa")
    void inFavorNeverLimited() {
        MaintenanceParameters p = params("{\"maxPerUnitAndYear\": 0}");
        assertThatCode(() -> RectificationLimitRule.check(p, m("100.00"), m("80.00"), 99))
                .doesNotThrowAnyException();
        assertThatCode(() -> RectificationLimitRule.check(p, m("100.00"), m("100.00"), 99))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sem teto numerico ({}): contra o usuario passa; diferenca e o total puro, sem encargos")
    void noNumericCapAndDifference() {
        MaintenanceParameters p = params("{}");
        assertThatCode(() -> RectificationLimitRule.check(p, m("100.00"), m("150.00"), 50))
                .doesNotThrowAnyException();
        assertThat(RectificationLimitRule.differenceWithoutCharges(m("100.00"), m("120.00")))
                .isEqualByComparingTo("20.00");
        assertThat(RectificationLimitRule.differenceWithoutCharges(m("100.00"), m("80.00")))
                .isEqualByComparingTo("-20.00");
    }
}
