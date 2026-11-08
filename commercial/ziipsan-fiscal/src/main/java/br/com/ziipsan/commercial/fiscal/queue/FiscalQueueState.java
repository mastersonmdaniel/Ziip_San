package br.com.ziipsan.commercial.fiscal.queue;

/**
 * Estados da FILA do operador (aceite da P2.33), derivados do estado do documento
 * fiscal da P2.6 por {@link FiscalQueueClassifier} — NÃO são estados novos do
 * documento: o documento continua com os 9 estados do dicionário (DAD-18) em
 * {@code fiscal_documents}; a fila classifica a pendência do operador sobre ele.
 *
 * <ul>
 *   <li>{@link #AUTHORIZED} — NFAg-e autorizada no dicionário da P2.6. A NFS-e
 *       avulsa transmitida NÃO recebe este estado: o classificador devolve
 *       {@code null} para ela (transmitida não é autorizada nesta fila);</li>
 *   <li>{@link #REJECTED} — rejeitada, com a explicação do catálogo da P2.16;</li>
 *   <li>{@link #CONTINGENCY} — emitida em contingência e ainda não transmitida;</li>
 *   <li>{@link #CANCELLED} — cancelada por evento;</li>
 *   <li>{@link #REPLACED} — substituída por outro documento;</li>
 *   <li>{@link #CANCELLATION_PENDING} — autorizada com cancelamento solicitado e
 *       ainda não confirmado: a pendência vive na V4_11, não no dicionário.</li>
 * </ul>
 */
public enum FiscalQueueState {
    AUTHORIZED,
    REJECTED,
    CONTINGENCY,
    CANCELLED,
    REPLACED,
    CANCELLATION_PENDING
}
