package br.com.ziipsan.commercial.collection.retorno;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.1 - split payment (LC 214/2025, LC 227/2026): um teste por modo de
 * recolhimento, valores calculados a mao sobre bruto de 1.000,00.
 */
class SplitPaymentReconcilerTest {

    private final SplitPaymentReconciler reconciler = new SplitPaymentReconciler();

    @Test
    void modoSegregadoNaLiquidacao() {
        // Tributo da conta 90,00 segregado; liquido esperado 1.000 - 90 = 910,00.
        var r = reconciler.reconcile(new SplitPaymentReconciler.SplitInput(
                SplitPaymentReconciler.SplitPaymentMode.SEGREGATED_AT_SETTLEMENT,
                new BigDecimal("1000.00"), new BigDecimal("90.00"), null,
                new BigDecimal("90.00"), new BigDecimal("910.00")));
        assertThat(r.status()).isEqualTo(SplitPaymentReconciler.ReconciliationStatus.RECONCILED);
        assertThat(r.expectedSegregated()).isEqualByComparingTo("90.00");
        assertThat(r.expectedNet()).isEqualByComparingTo("910.00");
        assertThat(r.difference()).isEqualByComparingTo("0.00");
    }

    @Test
    void modoSimplificadoPorPercentualDoPerfil() {
        // Percentual simplificado de 1%: segregado esperado 10,00; liquido 990,00.
        var r = reconciler.reconcile(new SplitPaymentReconciler.SplitInput(
                SplitPaymentReconciler.SplitPaymentMode.SIMPLIFIED_SEGREGATION,
                new BigDecimal("1000.00"), null, new BigDecimal("1.00"),
                new BigDecimal("10.00"), new BigDecimal("990.00")));
        assertThat(r.status()).isEqualTo(SplitPaymentReconciler.ReconciliationStatus.RECONCILED);
        assertThat(r.expectedSegregated()).isEqualByComparingTo("10.00");
        assertThat(r.expectedNet()).isEqualByComparingTo("990.00");
    }

    @Test
    void modoSemSegregacao() {
        var r = reconciler.reconcile(new SplitPaymentReconciler.SplitInput(
                SplitPaymentReconciler.SplitPaymentMode.NO_SEGREGATION,
                new BigDecimal("1000.00"), null, null,
                new BigDecimal("0.00"), new BigDecimal("1000.00")));
        assertThat(r.status()).isEqualTo(SplitPaymentReconciler.ReconciliationStatus.RECONCILED);
        assertThat(r.expectedNet()).isEqualByComparingTo("1000.00");
    }

    @Test
    void divergenciaELiquidoMenorSemSegregacaoCorrespondente() {
        // Segregado esperado 90,00, mas so 80,00 segregados e 910,00 liquidos:
        // 910 + 80 = 990 != 1.000 - divergente, diferenca de liquido 0 mas
        // segregado nao bate; e com liquido 900 a diferenca e -10,00.
        var r = reconciler.reconcile(new SplitPaymentReconciler.SplitInput(
                SplitPaymentReconciler.SplitPaymentMode.SEGREGATED_AT_SETTLEMENT,
                new BigDecimal("1000.00"), new BigDecimal("90.00"), null,
                new BigDecimal("80.00"), new BigDecimal("900.00")));
        assertThat(r.status()).isEqualTo(SplitPaymentReconciler.ReconciliationStatus.DIVERGENT);
        assertThat(r.difference()).isEqualByComparingTo("-10.00");
    }

    @Test
    void modoAusenteEErroExplicitoNuncaPresuncao() {
        assertThatThrownBy(() -> reconciler.reconcile(new SplitPaymentReconciler.SplitInput(
                null, new BigDecimal("1000.00"), null, null,
                new BigDecimal("0.00"), new BigDecimal("1000.00"))))
                .isInstanceOf(NullPointerException.class);
    }
}
