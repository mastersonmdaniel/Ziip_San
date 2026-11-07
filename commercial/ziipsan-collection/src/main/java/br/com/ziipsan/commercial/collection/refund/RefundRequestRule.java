package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.DoubleRefundSetting;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundDestination;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundMode;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Abertura da devolucao por OS {@code REFUND_REQUEST} (P3.29): a devolucao
 * nasce LIGADA ao pagamento de origem. Sem pagamento de origem, ou com valor
 * divergente do pagamento de origem, ela nasce PENDENTE com o motivo
 * ({@link RefundPendingReason}) - nao e executada, nao e recusada em
 * silencio e nenhum valor e inventado para "fazer fechar".
 *
 * <p>Fontes do pedido ({@code source}):
 * <ul>
 *   <li>{@code P3_1_CLASSIFICATION}: a classificacao da P3.1 marcou
 *       duplicidade ({@code DUPLICATE_REFUND_HANDOFF});</li>
 *   <li>{@code P3_2_HYBRID_SECOND_PAYMENT}: o segundo pagamento da cobranca
 *       hibrida (RAS-19; {@code HybridSettlementCoordinator} da P3.2 devolve
 *       {@code SECOND_PAYMENT_REFUND_HANDOFF}) - chega SEMPRE como
 *       duplicidade;</li>
 *   <li>{@code MANUAL_REQUEST}: pedido manual fundamentado.</li>
 * </ul>
 *
 * <p>Regra pura: devolve a decisao de abertura; quem grava a OS, a linha de
 * {@code refund_requests} e o destino cifrado e a fiacao transacional.
 */
public final class RefundRequestRule {

    public enum Source { P3_1_CLASSIFICATION, P3_2_HYBRID_SECOND_PAYMENT, MANUAL_REQUEST }

    public enum OpeningStatus { READY, PENDING }

    /**
     * @param originPaymentId      pagamento de origem; nulo = pendencia
     * @param originPaymentAmount  valor bruto do pagamento de origem (referencia da divergencia)
     * @param requestedBaseAmount  base pedida da devolucao
     * @param expectedBaseAmount   base esperada quando conhecida pela origem
     *                             (duplicidade hibrida: o valor do segundo pagamento);
     *                             nula quando a fonte nao a define
     * @param doubleWaived         o operador pede o AFASTAMENTO do dobro da
     *                             cobranca indevida (devolver o valor simples).
     *                             Com o dobro ligado, afastar sem
     *                             {@code doubleWaiverJustification} e RECUSADO
     *                             pela guarda
     *                             {@link RefundAmountCalculator#requireWaiverWhenDoubleOmitted}
     *                             ligada em {@link #open} (correcao pos-revisao M1:
     *                             antes a guarda era orfa, so o teste a chamava).
     */
    public record OpeningInput(UUID serviceOrderId, Source source, RefundType type,
                               UUID originPaymentId, BigDecimal originPaymentAmount,
                               BigDecimal requestedBaseAmount, BigDecimal expectedBaseAmount,
                               DoubleRefundSetting doubleSetting, String doubleWaiverJustification,
                               boolean doubleWaived) {

        /** Abertura sem pedido de afastamento do dobro (caminho comum). */
        public OpeningInput(UUID serviceOrderId, Source source, RefundType type,
                            UUID originPaymentId, BigDecimal originPaymentAmount,
                            BigDecimal requestedBaseAmount, BigDecimal expectedBaseAmount,
                            DoubleRefundSetting doubleSetting, String doubleWaiverJustification) {
            this(serviceOrderId, source, type, originPaymentId, originPaymentAmount,
                    requestedBaseAmount, expectedBaseAmount, doubleSetting,
                    doubleWaiverJustification, false);
        }
    }

    /**
     * @param calculated o calculo do valor (nulo quando pendente: sem origem
     *                   ou divergente, NAO se calcula devolucao)
     */
    public record OpeningDecision(OpeningStatus status, RefundPendingReason pendingReason,
                                  RefundAmountCalculator.RefundAmount calculated) {
        public boolean isPending() {
            return status == OpeningStatus.PENDING;
        }
    }

    private RefundRequestRule() {
    }

    public static OpeningDecision open(OpeningInput in) {
        Objects.requireNonNull(in, "entrada da abertura e obrigatoria");
        Objects.requireNonNull(in.serviceOrderId(), "OS REFUND_REQUEST e obrigatoria");
        Objects.requireNonNull(in.source(), "fonte do pedido e obrigatoria");
        Objects.requireNonNull(in.type(), "tipo da devolucao e obrigatorio");
        if (in.source() == Source.P3_2_HYBRID_SECOND_PAYMENT && in.type() != RefundType.DUPLICATE_PAYMENT) {
            throw new IllegalArgumentException("segundo pagamento da cobranca hibrida (RAS-19) "
                    + "chega sempre como duplicidade");
        }
        if (in.originPaymentId() == null) {
            return new OpeningDecision(OpeningStatus.PENDING,
                    RefundPendingReason.MISSING_ORIGIN_PAYMENT, null);
        }
        if (in.originPaymentAmount() == null || in.originPaymentAmount().signum() <= 0) {
            throw new IllegalArgumentException("valor do pagamento de origem deve ser positivo: "
                    + in.originPaymentAmount());
        }
        if (in.requestedBaseAmount() == null || in.requestedBaseAmount().signum() <= 0) {
            throw new IllegalArgumentException("base pedida deve ser positiva: " + in.requestedBaseAmount());
        }
        BigDecimal reference = in.expectedBaseAmount() != null ? in.expectedBaseAmount()
                : in.originPaymentAmount();
        if (in.requestedBaseAmount().compareTo(reference) != 0
                || in.requestedBaseAmount().compareTo(in.originPaymentAmount()) > 0) {
            return new OpeningDecision(OpeningStatus.PENDING,
                    RefundPendingReason.AMOUNT_DIVERGENCE, null);
        }
        // Guarda do afastamento do dobro LIGADA no fluxo (M1): quem abre
        // pedindo o valor simples (doubleWaived) com o dobro ligado precisa
        // da justificativa; sem ela a abertura e recusada - nao se aplica o
        // dobro em silencio nem se devolve simples em silencio.
        if (in.doubleWaived()) {
            RefundAmountCalculator.requireWaiverWhenDoubleOmitted(in.type(), in.doubleSetting(),
                    false, in.doubleWaiverJustification());
        }
        var calculated = RefundAmountCalculator.calculate(in.requestedBaseAmount(), in.type(),
                in.doubleSetting(), in.doubleWaiverJustification());
        return new OpeningDecision(OpeningStatus.READY, null, calculated);
    }

    /**
     * Efeito do encerramento conforme {@code pay.refund_mode}: o parametro
     * fixo decide; {@code USER_CHOICE} (padrao de fabrica) exige a escolha
     * registrada do usuario - sem escolha, o encerramento e recusado com
     * erro explicito (a devolucao continua aberta, nao e chutada).
     */
    public static RefundMode effectiveMode(RefundMode configured, RefundMode userChoice) {
        Objects.requireNonNull(configured, "pay.refund_mode e obrigatorio");
        return switch (configured) {
            case CREDIT_NEXT_BILL, BANK_TRANSFER -> configured;
            case USER_CHOICE -> {
                if (userChoice == null || userChoice == RefundMode.USER_CHOICE) {
                    throw new IllegalArgumentException("pay.refund_mode=USER_CHOICE exige a escolha "
                            + "do usuario (CREDIT_NEXT_BILL ou BANK_TRANSFER) para encerrar");
                }
                yield userChoice;
            }
        };
    }

    /** Devolucao bancaria exige destino parametrizado; credito nao usa destino bancario. */
    public static void requireDestinationForBankRefund(RefundMode effectiveMode, RefundDestination destination,
                                                       String destinationCiphertext) {
        if (effectiveMode == RefundMode.BANK_TRANSFER) {
            if (destination == null) {
                throw new IllegalArgumentException("devolucao bancaria exige pay.refund_destination");
            }
            if (destinationCiphertext == null || destinationCiphertext.isBlank()) {
                throw new IllegalArgumentException("devolucao bancaria exige o destino cifrado gravado");
            }
        }
    }
}
