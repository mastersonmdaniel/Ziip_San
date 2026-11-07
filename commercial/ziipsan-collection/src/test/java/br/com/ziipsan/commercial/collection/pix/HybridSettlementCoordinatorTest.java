package br.com.ziipsan.commercial.collection.pix;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.2 - baixa unica do hibrido: um teste por caso da matriz boleto x Pix. */
class HybridSettlementCoordinatorTest {

    private static final UUID PAYMENT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void pixPagoPrimeiroBaixaEPixNaoCancela() {
        var result = HybridSettlementCoordinator.onPayment(
                HybridSettlementCoordinator.DocumentStatus.ABERTO,
                HybridSettlementCoordinator.PaidChannel.PIX, PAYMENT);
        assertThat(result.outcome()).isEqualTo(HybridSettlementCoordinator.Outcome.SETTLED_BY_PIX);
        assertThat(result.newStatus()).isEqualTo(HybridSettlementCoordinator.DocumentStatus.BAIXADO_PIX);
        assertThat(result.cancelPixCharge()).isFalse();
    }

    @Test
    void boletoPagoPrimeiroBaixaECancelaOPix() {
        var result = HybridSettlementCoordinator.onPayment(
                HybridSettlementCoordinator.DocumentStatus.ABERTO,
                HybridSettlementCoordinator.PaidChannel.BOLETO, PAYMENT);
        assertThat(result.outcome()).isEqualTo(HybridSettlementCoordinator.Outcome.SETTLED_BY_SLIP);
        assertThat(result.newStatus()).isEqualTo(HybridSettlementCoordinator.DocumentStatus.BAIXADO_BOLETO);
        assertThat(result.cancelPixCharge()).isTrue(); // Pix Cobranca cancelado quando o boleto e pago
    }

    @Test
    void segundoPagamentoPixAposBoletoVaiParaDevolucao() {
        var result = HybridSettlementCoordinator.onPayment(
                HybridSettlementCoordinator.DocumentStatus.BAIXADO_BOLETO,
                HybridSettlementCoordinator.PaidChannel.PIX, PAYMENT);
        assertThat(result.outcome())
                .isEqualTo(HybridSettlementCoordinator.Outcome.SECOND_PAYMENT_REFUND_HANDOFF);
        assertThat(result.refundPaymentId()).isEqualTo(PAYMENT);
        assertThat(result.newStatus()).isEqualTo(HybridSettlementCoordinator.DocumentStatus.BAIXADO_BOLETO);
    }

    @Test
    void segundoPagamentoBoletoAposPixVaiParaDevolucao() {
        var result = HybridSettlementCoordinator.onPayment(
                HybridSettlementCoordinator.DocumentStatus.BAIXADO_PIX,
                HybridSettlementCoordinator.PaidChannel.BOLETO, PAYMENT);
        assertThat(result.outcome())
                .isEqualTo(HybridSettlementCoordinator.Outcome.SECOND_PAYMENT_REFUND_HANDOFF);
        assertThat(result.refundPaymentId()).isEqualTo(PAYMENT);
    }

    @Test
    void documentoCanceladoNaoRecebePagamento() {
        assertThatThrownBy(() -> HybridSettlementCoordinator.onPayment(
                HybridSettlementCoordinator.DocumentStatus.CANCELADO,
                HybridSettlementCoordinator.PaidChannel.PIX, PAYMENT))
                .isInstanceOf(IllegalStateException.class);
    }
}
