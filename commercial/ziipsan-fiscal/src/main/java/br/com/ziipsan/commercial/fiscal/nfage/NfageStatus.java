package br.com.ziipsan.commercial.fiscal.nfage;

/**
 * Estados do documento fiscal da NFAg-e, exatamente os 9 do dicionário fiscal
 * (23b §3.13, DAD-18) gravados em {@code fiscal_documents} (V4_5): o documento nasce
 * {@link #RESERVED} (série/número reservados em {@code fiscal_series}), é assinado
 * ({@link #SIGNED}), transmitido ({@link #TRANSMITTED}), autorizado
 * ({@link #AUTHORIZED}) ou rejeitado ({@link #REJECTED}) pela SEFAZ; transmissão sem
 * retorno conclusivo vai a {@link #PENDING_RECONCILIATION}; documento autorizado sai
 * por evento de cancelamento ({@link #CANCELLED}) ou por substituição
 * ({@link #REPLACED}, com o substituto ligado em {@code replaced_by_id}); número
 * reservado e não utilizado é inutilizado ({@link #VOIDED}).
 *
 * <p>Documento <b>vivo</b> (o que a idempotência da emissão devolve e o que a unicidade
 * de origem protege) é o que está em RESERVED, SIGNED, TRANSMITTED, AUTHORIZED ou
 * PENDING_RECONCILIATION — ver {@link NfageStateMachine#isLive(NfageStatus)}.
 */
public enum NfageStatus {
    RESERVED,
    SIGNED,
    TRANSMITTED,
    AUTHORIZED,
    REJECTED,
    PENDING_RECONCILIATION,
    CANCELLED,
    REPLACED,
    VOIDED
}
