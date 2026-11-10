package br.com.ziipsan.operations.metering.critique;

/** Ciclo de vida de uma excepcao da fila (II-2, E3). Estados terminais: ACCEPTED, CORRECTED. */
public enum ExceptionStatus {
    PENDING,
    ACCEPTED,
    CORRECTED,
    REREADING_REQUESTED
}
