package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Uma amostra horária de telemetria (ED-17): o volume de UMA hora de um canal
 * de um ponto de medição. É o dado da curva horária — tratado como dado
 * pessoal (o consumo horário revela a rotina do domicílio), com minimização:
 * a amostra carrega só ponto, canal, hora, volumes e estado; nenhum dado do
 * titular viaja nela.
 *
 * @param hourStart        início da hora, com o fuso do ponto de medição
 * @param volumeLitres     volume da hora em litros; nunca negativo
 * @param reverseVolumeLitres volume reverso da hora em litros; nulo = não informado (zero NÃO é presumido na entrada: quem monta a amostra a partir do dispositivo informa 0 explicitamente quando o dispositivo mede reverso)
 * @param deviceAlarms     alarmes sinalizados pelo próprio dispositivo nesta hora (tubo vazio, violação, bateria, estouro, fluxo reverso); vazio quando nenhum
 */
public record HourlySample(UUID measuringPointId, UUID channelId, UUID unitId,
                           OffsetDateTime hourStart, BigDecimal volumeLitres,
                           BigDecimal reverseVolumeLitres, DataState state,
                           Set<MeterAlarmKind> deviceAlarms) {

    public HourlySample {
        TelemetryInput.notNull(measuringPointId, "measuringPointId");
        TelemetryInput.notNull(channelId, "channelId");
        TelemetryInput.notNull(unitId, "unitId");
        TelemetryInput.notNull(hourStart, "hourStart");
        TelemetryInput.notNull(state, "state");
        TelemetryInput.require(volumeLitres != null && volumeLitres.signum() >= 0,
                MeteringErrorCode.INVALID_INPUT, "volumeLitres é obrigatório e não pode ser negativo");
        TelemetryInput.require(reverseVolumeLitres == null || reverseVolumeLitres.signum() >= 0,
                MeteringErrorCode.INVALID_INPUT, "reverseVolumeLitres não pode ser negativo");
        deviceAlarms = deviceAlarms == null ? Set.of() : Set.copyOf(deviceAlarms);
    }

    /** O volume reverso da hora; 0 quando o dispositivo não o informou nesta amostra. */
    public BigDecimal reverseOrZero() {
        return reverseVolumeLitres == null ? BigDecimal.ZERO : reverseVolumeLitres;
    }

    public boolean hasFlow() {
        return volumeLitres.signum() > 0;
    }
}
