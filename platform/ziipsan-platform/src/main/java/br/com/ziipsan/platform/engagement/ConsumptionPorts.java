package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas de consumo da ED-101. O consumo de referencia da meta e o consumo
 * FATURADO do periodo (billing); a telemetria da ED-17, QUANDO HOUVER
 * (aceite), fornece o agregado do periodo e tem precedencia para o
 * acompanhamento — o adaptador da integracao agrega a serie horaria da
 * ED-17 por unidade/periodo (a curva horaria em si continua sob a regra de
 * visibilidade da ED-17; aqui so viaja o total mensal da PROPRIA unidade).
 *
 * <p>Ausencia nas duas fontes e {@code NO_DATA} — nunca zero presumido
 * (mesma regra da ED-17: zero nao e presumido na entrada).
 */
public final class ConsumptionPorts {

    private ConsumptionPorts() {
    }

    /** Consumo faturado (m3) da unidade no periodo, do billing. */
    public interface BilledConsumptionPort {
        Optional<BigDecimal> billedM3(UUID tenantId, UUID unitId, YearMonth period);
    }

    /** Agregado de telemetria (m3) da unidade no periodo, da ED-17. */
    public interface TelemetryConsumptionPort {
        Optional<BigDecimal> telemetryM3(UUID tenantId, UUID unitId, YearMonth period);
    }
}
