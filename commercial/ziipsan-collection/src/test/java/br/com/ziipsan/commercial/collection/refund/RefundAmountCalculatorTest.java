package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.DoubleRefundSetting;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.29 - valores calculados a mao. Base recorrente: cobranca indevida de
 * 87,50 -> dobro = 175,00. Duplicidade de 100,00 -> devolve 100,00.
 */
class RefundAmountCalculatorTest {

    private static final DoubleRefundSetting DOUBLE_ON = new DoubleRefundSetting(true, null);
    private static final DoubleRefundSetting DOUBLE_OFF =
            new DoubleRefundSetting(false, "Norma citada pelo tenant");

    @Test
    void duplicidadeDevolveABaseSemDobro() {
        var a = RefundAmountCalculator.calculate(new BigDecimal("100.00"),
                RefundType.DUPLICATE_PAYMENT, DOUBLE_ON, null);
        assertThat(a.refundAmount()).isEqualByComparingTo("100.00");
        assertThat(a.doubleApplied()).isFalse();
    }

    @Test
    void cobrancaIndevidaComDobroLigadoDevolveODobro() {
        // 87,50 x 2 = 175,00 (CDC, art. 42, paragrafo unico).
        var a = RefundAmountCalculator.calculate(new BigDecimal("87.50"),
                RefundType.UNDUE_CHARGE, DOUBLE_ON, null);
        assertThat(a.refundAmount()).isEqualByComparingTo("175.00");
        assertThat(a.doubleApplied()).isTrue();
    }

    @Test
    void cobrancaIndevidaComDobroAfastadoExigeJustificativaEDevolveABase() {
        var a = RefundAmountCalculator.calculate(new BigDecimal("87.50"),
                RefundType.UNDUE_CHARGE, DOUBLE_ON, "Cobranca reconhecida como devida em parte acordada");
        assertThat(a.refundAmount()).isEqualByComparingTo("87.50");
        assertThat(a.doubleApplied()).isFalse();
        // Sem justificativa, a guarda do afastamento recusa.
        assertThatThrownBy(() -> RefundAmountCalculator.requireWaiverWhenDoubleOmitted(
                RefundType.UNDUE_CHARGE, DOUBLE_ON, false, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa obrigatoria");
    }

    @Test
    void cobrancaIndevidaComDobroDesligadoPeloParametroDevolveABase() {
        var a = RefundAmountCalculator.calculate(new BigDecimal("87.50"),
                RefundType.UNDUE_CHARGE, DOUBLE_OFF, null);
        assertThat(a.refundAmount()).isEqualByComparingTo("87.50");
        assertThat(a.doubleApplied()).isFalse();
    }

    @Test
    void baseNaoPositivaERecusada() {
        assertThatThrownBy(() -> RefundAmountCalculator.calculate(new BigDecimal("0.00"),
                RefundType.DUPLICATE_PAYMENT, DOUBLE_ON, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
