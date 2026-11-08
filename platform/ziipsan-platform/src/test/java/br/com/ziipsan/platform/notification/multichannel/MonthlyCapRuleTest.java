package br.com.ziipsan.platform.notification.multichannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teto mensal calculado à mão: teto 1.000,00 — 80% = 800,00.
 * 750 + 50 = 800 (travessia dos 80%); 950 + 50 = 1.000 (pousa no teto);
 * 999 + 2 = 1.001 (ultrapassa e bloqueia).
 */
class MonthlyCapRuleTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("abaixo de 80%: WITHIN, sem alerta e sem bloqueio")
    void withinBelow80() {
        MonthlyCapRule.CapDecision d = MonthlyCapRule.evaluate(bd("1000.00"), bd("700.00"), bd("50.00"));
        assertThat(d.level()).isEqualTo(MonthlyCapRule.CapLevel.WITHIN);
        assertThat(d.projectedSpend()).isEqualByComparingTo("750.00");
        assertThat(d.blocked()).isFalse();
        assertThat(d.alert80Raised()).isFalse();
        assertThat(d.alert100Raised()).isFalse();
    }

    @Test
    @DisplayName("travessia dos 80% neste envio: ALERT_80 com alerta levantado")
    void crosses80() {
        MonthlyCapRule.CapDecision d = MonthlyCapRule.evaluate(bd("1000.00"), bd("750.00"), bd("50.00"));
        assertThat(d.level()).isEqualTo(MonthlyCapRule.CapLevel.ALERT_80);
        assertThat(d.alert80Raised()).isTrue();
        assertThat(d.alert100Raised()).isFalse();
        assertThat(d.blocked()).isFalse();
    }

    @Test
    @DisplayName("já acima de 80%: nível ALERT_80 sem levantar o alerta de novo")
    void alreadyAbove80DoesNotRaiseAgain() {
        MonthlyCapRule.CapDecision d = MonthlyCapRule.evaluate(bd("1000.00"), bd("850.00"), bd("10.00"));
        assertThat(d.level()).isEqualTo(MonthlyCapRule.CapLevel.ALERT_80);
        assertThat(d.alert80Raised()).isFalse();
        assertThat(d.blocked()).isFalse();
    }

    @Test
    @DisplayName("pousar exatamente no teto: REACHED_100 com alerta, sem bloqueio")
    void landsExactlyOnCap() {
        MonthlyCapRule.CapDecision d = MonthlyCapRule.evaluate(bd("1000.00"), bd("950.00"), bd("50.00"));
        assertThat(d.level()).isEqualTo(MonthlyCapRule.CapLevel.REACHED_100);
        assertThat(d.alert100Raised()).isTrue();
        assertThat(d.blocked()).isFalse();
    }

    @Test
    @DisplayName("ultrapassar o teto bloqueia o envio")
    void exceedingCapBlocks() {
        MonthlyCapRule.CapDecision d = MonthlyCapRule.evaluate(bd("1000.00"), bd("999.00"), bd("2.00"));
        assertThat(d.level()).isEqualTo(MonthlyCapRule.CapLevel.REACHED_100);
        assertThat(d.blocked()).isTrue();
    }

    @Test
    @DisplayName("gasto já no teto: qualquer estimativa positiva bloqueia")
    void atCapBlocksAnyFurther() {
        MonthlyCapRule.CapDecision d = MonthlyCapRule.evaluate(bd("1000.00"), bd("1000.00"), bd("0.01"));
        assertThat(d.blocked()).isTrue();
        assertThat(d.alert100Raised()).isFalse();
    }

    @Test
    @DisplayName("teto não positivo é recusado")
    void rejectsNonPositiveCap() {
        assertThatThrownBy(() -> MonthlyCapRule.evaluate(bd("0"), bd("0"), bd("1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("gasto ou estimativa negativos são recusados")
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> MonthlyCapRule.evaluate(bd("1000.00"), bd("-1.00"), bd("1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MonthlyCapRule.evaluate(bd("1000.00"), bd("0"), bd("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
