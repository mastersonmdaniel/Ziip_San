package br.com.ziipsan.commercial.collection.retorno;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Classificacao do pagamento e o efeito que ela grava (P3.1).
 *
 * <p>Regras (todas com teste calculado a mao):
 * <ul>
 *   <li>igual ao devido -&gt; baixa da versao vigente da conta ({@link Effect#SETTLEMENT});</li>
 *   <li>a menor/a maior dentro de {@code pay.adjust_tolerance} -&gt; baixa e a diferenca
 *       vira debito a cobrar / credito a realizar de ajuste na CONTA SEGUINTE;</li>
 *   <li>diferenca fora da tolerancia -&gt; nenhum efeito automatico
 *       ({@link Effect#NONE_PENDING_MANUAL}); classificacao manual com aprovacao dupla;</li>
 *   <li>conta ja paga -&gt; duplicidade: aviso ao usuario (NR 11, art. 37) e repasse a
 *       devolucao da P3.29 ({@link Effect#DUPLICATE_REFUND_HANDOFF});</li>
 *   <li>conta prescrita -&gt; recebimento valido, sem devolucao (CC, art. 882): baixa e
 *       evento de recuperacao de credito ({@link Effect#CREDIT_RECOVERY}) para a ED-32;</li>
 *   <li>transferencia -&gt; so via OS {@code PAYMENT_TRANSFER}, manual, aprovacao dupla;</li>
 *   <li>sem conta -&gt; nao identificado, fila da P3.29, sem baixa.</li>
 * </ul>
 *
 * <p>Sem acrescimo sobre conta em revisao aberta: quando {@code billUnderOpenReview},
 * multa/juros informados sao ignorados no valor devido da classificacao (o acrescimo
 * nao pode nascer enquanto a propria conta esta sendo revista).
 *
 * <p>Este classificador e puro: decide situacao e efeito; quem grava baixa, debito,
 * credito, aviso e evento e a fiacao transacional da integracao, a partir da decisao.
 */
public final class PaymentClassifier {

    public enum BillState { OPEN, PAID, PRESCRIBED }

    public enum Effect {
        SETTLEMENT,
        NEXT_BILL_DEBIT,
        NEXT_BILL_CREDIT,
        NONE_PENDING_MANUAL,
        DUPLICATE_REFUND_HANDOFF,
        CREDIT_RECOVERY,
        TRANSFER,
        UNIDENTIFIED_QUEUE
    }

    /**
     * @param billId              conta identificada; nulo = pagamento nao identificado
     * @param billState           situacao da conta identificada (obrigatoria se ha conta)
     * @param billAmount          valor da versao vigente da conta (sem acrescimos)
     * @param accruedCharges      multa/juros calculados ate o pagamento (ignorados se revisao aberta)
     * @param billUnderOpenReview conta com revisao aberta (P2.20)
     * @param paidAmount          valor pago (bruto da baixa, antes de split/retencao)
     * @param adjustTolerance     {@code pay.adjust_tolerance} vigente
     * @param transferToBillId    conta de destino quando o operador classifica como transferencia
     */
    public record ClassificationInput(UUID billId, BillState billState, BigDecimal billAmount,
                                      BigDecimal accruedCharges, boolean billUnderOpenReview,
                                      BigDecimal paidAmount, BigDecimal adjustTolerance,
                                      UUID transferToBillId) {
    }

    /**
     * @param effectAmount      valor do efeito (residual a debitar/creditar, valor da
     *                          duplicidade ou da recuperacao); zero quando nao se aplica
     * @param settledAmount     quanto da conta foi baixado nesta classificacao
     * @param requiresManualApproval classificacao manual (aprovacao dupla) antes do efeito
     * @param notifyUserOfDuplicate  aviso de duplicidade ao usuario (NR 11, art. 37)
     * @param refundable        verdadeiro somente na duplicidade (devolucao pela P3.29);
     *                          conta prescrita NUNCA e reembolsavel (CC, art. 882)
     * @param emitsCreditRecoveryEvent evento de recuperacao de credito para a ED-32
     */
    public record ClassificationDecision(PaymentSituation situation, Effect effect, BigDecimal effectAmount,
                                         BigDecimal settledAmount, boolean requiresManualApproval,
                                         boolean notifyUserOfDuplicate, boolean refundable,
                                         boolean emitsCreditRecoveryEvent) {
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public ClassificationDecision classify(ClassificationInput in) {
        Objects.requireNonNull(in, "entrada da classificacao e obrigatoria");
        if (in.paidAmount() == null || in.paidAmount().signum() <= 0) {
            throw new IllegalArgumentException("valor pago deve ser positivo: " + in.paidAmount());
        }
        if (in.adjustTolerance() == null || in.adjustTolerance().signum() < 0) {
            throw new IllegalArgumentException("pay.adjust_tolerance deve ser >= 0: " + in.adjustTolerance());
        }
        if (in.billId() == null) {
            return new ClassificationDecision(PaymentSituation.UNIDENTIFIED, Effect.UNIDENTIFIED_QUEUE,
                    ZERO, ZERO, false, false, false, false);
        }
        Objects.requireNonNull(in.billState(), "situacao da conta e obrigatoria quando ha conta identificada");
        if (in.billAmount() == null || in.billAmount().signum() < 0) {
            throw new IllegalArgumentException("valor da conta deve ser >= 0: " + in.billAmount());
        }
        if (in.transferToBillId() != null) {
            if (in.transferToBillId().equals(in.billId())) {
                throw new IllegalArgumentException("transferencia para a propria conta nao e transferencia");
            }
            return new ClassificationDecision(PaymentSituation.PAYMENT_TRANSFER, Effect.TRANSFER,
                    in.paidAmount(), ZERO, true, false, false, false);
        }
        if (in.billState() == BillState.PAID) {
            return new ClassificationDecision(PaymentSituation.DUPLICATE_PAYMENT, Effect.DUPLICATE_REFUND_HANDOFF,
                    in.paidAmount(), ZERO, false, true, true, false);
        }
        if (in.billState() == BillState.PRESCRIBED) {
            // CC, art. 882: quem paga divida prescrita nao tem direito a repeticao.
            return new ClassificationDecision(PaymentSituation.PRESCRIBED_BILL_PAYMENT, Effect.CREDIT_RECOVERY,
                    in.paidAmount(), in.paidAmount(), false, false, false, true);
        }
        // Conta aberta: sem acrescimo se houver revisao aberta.
        BigDecimal charges = in.billUnderOpenReview() || in.accruedCharges() == null ? ZERO : in.accruedCharges();
        if (charges.signum() < 0) {
            throw new IllegalArgumentException("acrescimos nao podem ser negativos: " + charges);
        }
        BigDecimal due = in.billAmount().add(charges);
        int cmp = in.paidAmount().compareTo(due);
        if (cmp == 0) {
            return new ClassificationDecision(PaymentSituation.EXACT_SETTLEMENT, Effect.SETTLEMENT,
                    ZERO, due, false, false, false, false);
        }
        BigDecimal difference = in.paidAmount().subtract(due).abs();
        if (difference.compareTo(in.adjustTolerance()) > 0) {
            return new ClassificationDecision(PaymentSituation.DIFFERENCE_BEYOND_TOLERANCE,
                    Effect.NONE_PENDING_MANUAL, difference, ZERO, true, false, false, false);
        }
        if (cmp < 0) {
            return new ClassificationDecision(PaymentSituation.UNDERPAID_WITHIN_TOLERANCE,
                    Effect.NEXT_BILL_DEBIT, difference, in.paidAmount(), false, false, false, false);
        }
        return new ClassificationDecision(PaymentSituation.OVERPAID_WITHIN_TOLERANCE,
                Effect.NEXT_BILL_CREDIT, difference, due, false, false, false, false);
    }
}
