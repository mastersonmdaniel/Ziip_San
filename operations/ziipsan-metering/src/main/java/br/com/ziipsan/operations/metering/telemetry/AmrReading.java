package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma leitura AMR recebida por REST ou por arquivo (ED-17): a leitura do
 * mostrador que a VEE valida antes de virar leitura de faturamento
 * (origem {@code TELEMETRY} em {@code meter_readings}).
 *
 * @param readAt      instante da leitura, com o fuso do ponto de medição
 * @param dialReading leitura do mostrador; nula quando o dispositivo não
 *                    entregou leitura nesta coleta (a VEE estima ou rejeita —
 *                    nunca presume zero)
 * @param externalRef referência do sistema de origem; é a chave de
 *                    idempotência da leitura (o reenvio com a mesma
 *                    referência não duplica)
 */
public record AmrReading(UUID measuringPointId, UUID channelId, OffsetDateTime readAt,
                         Integer dialReading, DataState state, String externalRef) {

    public AmrReading {
        TelemetryInput.notNull(measuringPointId, "measuringPointId");
        TelemetryInput.notNull(channelId, "channelId");
        TelemetryInput.notNull(readAt, "readAt");
        TelemetryInput.notNull(state, "state");
        TelemetryInput.require(externalRef != null && !externalRef.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "externalRef (chave de idempotência) é obrigatória");
    }
}
