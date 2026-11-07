package br.com.ziipsan.commercial.collection.retorno;

/**
 * Situacoes de classificacao de um pagamento (P3.1). Cada situacao tem ao menos um
 * teste em {@code PaymentClassifierTest} - "um teste por situacao de classificacao".
 */
public enum PaymentSituation {

    /** Valor pago igual ao devido: baixa integral da versao vigente da conta. */
    EXACT_SETTLEMENT,

    /** Pago a menor, diferenca dentro de {@code pay.adjust_tolerance}: baixa e debito
     *  a cobrar do residual na conta seguinte. */
    UNDERPAID_WITHIN_TOLERANCE,

    /** Pago a maior, diferenca dentro da tolerancia: baixa e credito a realizar de
     *  ajuste na conta seguinte. */
    OVERPAID_WITHIN_TOLERANCE,

    /** Diferenca (a maior ou a menor) fora da tolerancia: nenhum efeito automatico;
     *  a classificacao fica pendente de classificacao manual com aprovacao dupla. */
    DIFFERENCE_BEYOND_TOLERANCE,

    /** Conta ja estava paga: pagamento em duplicidade. Aviso ao usuario
     *  (NR 11, art. 37) e devolucao conduzida pela P3.29 - aqui so o repasse. */
    DUPLICATE_PAYMENT,

    /** Conta prescrita: pagamento e recebimento valido, sem devolucao (CC, art. 882);
     *  conta marcada paga e evento de recuperacao de credito para a ED-32. */
    PRESCRIBED_BILL_PAYMENT,

    /** Pagamento pertence a outra conta: transferencia pela OS PAYMENT_TRANSFER,
     *  sempre manual com aprovacao dupla. */
    PAYMENT_TRANSFER,

    /** Sem conta correspondente: nao identificado; vai a fila da P3.29
     *  (OS PAYMENT_IDENTIFICATION), sem cliente ficticio e sem baixa. */
    UNIDENTIFIED
}
