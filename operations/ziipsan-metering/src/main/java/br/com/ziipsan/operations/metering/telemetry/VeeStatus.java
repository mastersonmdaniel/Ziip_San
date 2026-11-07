package br.com.ziipsan.operations.metering.telemetry;

/**
 * Situação de uma leitura de telemetria na VEE — validação, estimativa e
 * edição (ED-17). Só {@code VALIDATED}, {@code ESTIMATED} e {@code EDITED}
 * podem virar leitura de faturamento; {@code REJECTED} nunca promove.
 */
public enum VeeStatus {
    RECEIVED,
    VALIDATED,
    ESTIMATED,
    EDITED,
    REJECTED,
    PROMOTED
}
