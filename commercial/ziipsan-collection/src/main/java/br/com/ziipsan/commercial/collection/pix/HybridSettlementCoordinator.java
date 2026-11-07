package br.com.ziipsan.commercial.collection.pix;

import java.util.Objects;
import java.util.UUID;

/**
 * Baixa unica do documento hibrido (P3.2): Pix Cobranca e boleto sao duas
 * portas para o MESMO documento; quem paga primeiro baixa, e o outro canal
 * e encerrado.
 *
 * <ul>
 *   <li>boleto pago primeiro -&gt; documento {@code BAIXADO_BOLETO} e a
 *       cobranca Pix e CANCELADA no PSP ({@link PspPixPort#cancelCharge});</li>
 *   <li>Pix pago primeiro -&gt; documento {@code BAIXADO_PIX} e o boleto nao
 *       pode mais baixar (quem grava a baixa do boleto consulta este estado);</li>
 *   <li>segundo pagamento, por qualquer canal -&gt; NAO baixa de novo:
 *       {@link Outcome#SECOND_PAYMENT_REFUND_HANDOFF}, encaminhado a
 *       devolucao da P3.29 (a devolucao em si e da P3.29, nao desta tarefa).</li>
 * </ul>
 *
 * <p>Coordenador puro sobre o estado do documento: a persistencia e o
 * cancelamento efetivo no PSP sao da fiacao, a partir do {@link Result}.
 */
public final class HybridSettlementCoordinator {

    public enum DocumentStatus { ABERTO, BAIXADO_PIX, BAIXADO_BOLETO, CANCELADO }

    public enum PaidChannel { PIX, BOLETO }

    public enum Outcome { SETTLED_BY_PIX, SETTLED_BY_SLIP, SECOND_PAYMENT_REFUND_HANDOFF }

    /**
     * @param cancelPixCharge a fiacao deve cancelar a cobranca Pix no PSP
     *                        (boleto pago primeiro)
     */
    public record Result(Outcome outcome, DocumentStatus newStatus, boolean cancelPixCharge,
                         UUID refundPaymentId) {
    }

    private HybridSettlementCoordinator() {
    }

    public static Result onPayment(DocumentStatus current, PaidChannel channel, UUID paymentId) {
        Objects.requireNonNull(current, "estado do documento hibrido e obrigatorio");
        Objects.requireNonNull(channel, "canal do pagamento e obrigatorio");
        Objects.requireNonNull(paymentId, "pagamento e obrigatorio");
        if (current == DocumentStatus.CANCELADO) {
            throw new IllegalStateException("documento hibrido cancelado nao recebe pagamento: " + paymentId);
        }
        if (current != DocumentStatus.ABERTO) {
            // Segundo pagamento do mesmo documento: nunca segunda baixa.
            return new Result(Outcome.SECOND_PAYMENT_REFUND_HANDOFF, current, false, paymentId);
        }
        if (channel == PaidChannel.PIX) {
            return new Result(Outcome.SETTLED_BY_PIX, DocumentStatus.BAIXADO_PIX, false, null);
        }
        return new Result(Outcome.SETTLED_BY_SLIP, DocumentStatus.BAIXADO_BOLETO, true, null);
    }
}
