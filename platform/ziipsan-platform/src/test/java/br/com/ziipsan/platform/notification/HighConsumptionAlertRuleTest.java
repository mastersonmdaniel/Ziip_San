package br.com.ziipsan.platform.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Crítica de consumo anormal (P3.5 sobre a média da P2.5), calculada à mão:
 * média 10 m³, {@code alert.high_consumption_pct} = 50 → limite 15 m³.
 * Consumo 16 está ACIMA de 50% da média (dispara); 15 é exatamente o limite
 * (não está acima: não dispara); 14 está abaixo (não dispara).
 * Excesso de 16 sobre 10 = 60,00%.
 */
class HighConsumptionAlertRuleTest {

    private static final BigDecimal AVG = new BigDecimal("10");
    private static final BigDecimal PCT = new BigDecimal("50");

    @Test
    @DisplayName("limite = média x (1 + pct/100): 10 x 1,50 = 15")
    void thresholdHandCalculated() {
        assertThat(HighConsumptionAlertRule.threshold(AVG, PCT)).isEqualByComparingTo("15.00");
        assertThat(HighConsumptionAlertRule.threshold(new BigDecimal("12"), new BigDecimal("25")))
                .isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("TESTE DO ACEITE com a crítica disparada: consumo 16 com média 10 e pct 50 dispara o alerta")
    void firesAboveThreshold() {
        assertThat(HighConsumptionAlertRule.fires(new BigDecimal("16"), AVG, PCT)).isTrue();
        assertThat(HighConsumptionAlertRule.excessPct(new BigDecimal("16"), AVG))
                .isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("TESTE DO ACEITE sem disparo: consumo 14 não dispara, e o consumo IGUAL ao limite (15) também não — 'acima' é estrito")
    void doesNotFireAtOrBelowThreshold() {
        assertThat(HighConsumptionAlertRule.fires(new BigDecimal("14"), AVG, PCT)).isFalse();
        assertThat(HighConsumptionAlertRule.fires(new BigDecimal("15"), AVG, PCT)).isFalse();
    }

    @Test
    @DisplayName("média ausente, zero ou negativa torna a crítica impossível: erro explícito, nunca média presumida")
    void missingAverageIsExplicitError() {
        assertThatThrownBy(() -> HighConsumptionAlertRule.fires(new BigDecimal("16"), null, PCT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HighConsumptionAlertRule.fires(new BigDecimal("16"), BigDecimal.ZERO, PCT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HighConsumptionAlertRule.fires(new BigDecimal("16"), AVG, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
