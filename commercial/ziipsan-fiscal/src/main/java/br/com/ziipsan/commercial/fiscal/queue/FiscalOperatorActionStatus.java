package br.com.ziipsan.commercial.fiscal.queue;

/**
 * Estado da ação do operador na V4_11: {@code REQUESTED} (registrada, porta externa
 * ainda não chamada ou sem porta ligada — nenhum efeito externo é fingido),
 * {@code PENDING} (cancelamento solicitado à SEFAZ e ainda não confirmado),
 * {@code COMPLETED} (efeito confirmado pela porta) e {@code FAILED} (porta recusou).
 */
public enum FiscalOperatorActionStatus {
    REQUESTED,
    PENDING,
    COMPLETED,
    FAILED
}
