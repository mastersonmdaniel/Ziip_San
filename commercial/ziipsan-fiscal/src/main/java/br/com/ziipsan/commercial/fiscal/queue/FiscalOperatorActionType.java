package br.com.ziipsan.commercial.fiscal.queue;

/** Ações do operador registradas na trilha da V4_11 (uma linha por tentativa aceita). */
public enum FiscalOperatorActionType {
    RESEND,
    VOIDING,
    CANCELLATION_REQUEST
}
