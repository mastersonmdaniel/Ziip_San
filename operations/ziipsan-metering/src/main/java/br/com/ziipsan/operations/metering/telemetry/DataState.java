package br.com.ziipsan.operations.metering.telemetry;

/**
 * Estado do dado de telemetria (ED-17): como o valor chegou ao Ziip SAN.
 * Os quatro valores são os do aceite da ED-17 — medido, estimado, substituído
 * e atrasado — e acompanham o CHECK das tabelas de telemetria.
 */
public enum DataState {
    /** Valor medido pelo dispositivo e recebido sem intervenção. */
    MEASURED,
    /** Valor estimado pela VEE (sem leitura válida do dispositivo). */
    ESTIMATED,
    /** Valor substituído por edição registrada na VEE. */
    SUBSTITUTED,
    /** Valor medido, mas recebido fora do prazo do ciclo (chegou atrasado). */
    LATE
}
