package br.com.ziipsan.operations.metering.telemetry;

/**
 * Alarmes padronizados do medidor (ED-17). Os seis tipos são os do aceite:
 * fluxo contínuo ou vazamento, fluxo reverso, tubo vazio, violação, bateria
 * e estouro. {@code CONTINUOUS_FLOW} e {@code REVERSE_FLOW} também são
 * detectados pela regra sobre a série horária ({@link TelemetryAlarmRule});
 * os demais chegam como sinalização do próprio dispositivo na amostra.
 */
public enum MeterAlarmKind {
    /** Fluxo contínuo por horas seguidas: vazamento provável. */
    CONTINUOUS_FLOW,
    /** Fluxo reverso acima do limiar do tenant. */
    REVERSE_FLOW,
    /** Tubo vazio (sem água no ponto de medição). */
    EMPTY_PIPE,
    /** Violação do medidor ou do lacre. */
    TAMPERING,
    /** Bateria do dispositivo fraca. */
    BATTERY_LOW,
    /** Estouro: vazão ou leitura acima da capacidade do medidor. */
    OVERFLOW
}
