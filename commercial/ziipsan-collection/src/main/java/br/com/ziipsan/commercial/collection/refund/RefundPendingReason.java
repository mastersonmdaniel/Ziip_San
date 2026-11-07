package br.com.ziipsan.commercial.collection.refund;

/**
 * Motivo pelo qual uma devolucao fica PENDENTE (P3.29): pendencia e estado
 * explicito com motivo, nunca recusa silenciosa nem execucao com dado chutado.
 */
public enum RefundPendingReason {
    /** Sem pagamento de origem: nao ha o que vincular nem valor de referencia. */
    MISSING_ORIGIN_PAYMENT,
    /** Valor pedido diverge do valor do pagamento de origem (ou da base esperada). */
    AMOUNT_DIVERGENCE
}
