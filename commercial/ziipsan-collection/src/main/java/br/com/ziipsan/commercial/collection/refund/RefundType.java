package br.com.ziipsan.commercial.collection.refund;

/** Tipo da devolucao (P3.29): o que fundamenta o valor a devolver. */
public enum RefundType {
    /** Pagamento em duplicidade: devolve o valor pago a mais, sem dobro. */
    DUPLICATE_PAYMENT,
    /** Cobranca indevida: devolucao em dobro quando o parametro manda (CDC, art. 42). */
    UNDUE_CHARGE
}
