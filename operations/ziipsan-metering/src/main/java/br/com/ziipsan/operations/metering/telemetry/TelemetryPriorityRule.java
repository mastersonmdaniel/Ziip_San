package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.time.OffsetDateTime;

/**
 * A bifurcação de prioridade do ciclo (ED-17, ADR-0038): quando a unidade tem
 * leitura manual E leitura de telemetria promovida no mesmo ciclo, quem vale
 * é o que o parâmetro {@code meter.telemetry_priority_over_manual} do tenant
 * diz — com o parâmetro ligado, a telemetria; desligado, a manual. Sem as
 * duas candidatas, vale a que existe; sem nenhuma, erro explícito (o ciclo
 * não inventa leitura).
 */
public final class TelemetryPriorityRule {

    private TelemetryPriorityRule() {
    }

    public enum Source {
        TELEMETRY,
        MANUAL
    }

    /** Uma leitura candidata a leitura de faturamento do ciclo. */
    public record Candidate(Source source, int dialReading, OffsetDateTime readAt) {
    }

    public static Candidate choose(Candidate manual, Candidate telemetry,
                                   boolean telemetryPriorityOverManual) {
        if (manual == null && telemetry == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "o ciclo não tem leitura manual nem de telemetria para escolher");
        }
        if (manual == null) {
            return telemetry;
        }
        if (telemetry == null) {
            return manual;
        }
        return telemetryPriorityOverManual ? telemetry : manual;
    }
}
