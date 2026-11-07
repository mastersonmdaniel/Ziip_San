package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A retenção por resolução da telemetria (ED-17), calculada dos parâmetros
 * do tenant — nenhum prazo mora nesta classe:
 *
 * <ul>
 *   <li>série bruta: descartar o que é anterior a
 *       {@code agora − tele.retention_raw_days} dias;</li>
 *   <li>série horária: descartar os meses anteriores a
 *       {@code mês atual − tele.retention_hourly_months} meses (a partição do
 *       mês sai inteira, pelo padrão de partição da P2.3);</li>
 *   <li>consumo horário como dado pessoal: descartar o que é anterior a
 *       {@code agora − ami.retention_days} dias — a retenção curta da curva
 *       horária, com a base legal registrada no ponto de medição;</li>
 *   <li>os agregados seguem {@code tele.rollup_policy} na tabela de resumo
 *       ({@code telemetry_hourly_daily_summary}): a política é valor do
 *       tenant e é carregada no plano para o job de agregação aplicar.</li>
 * </ul>
 *
 * Prazo ausente ou não positivo é erro explícito: retenção sem parâmetro não
 * vira "reter para sempre" nem "apagar tudo" por omissão.
 */
public final class RetentionPolicy {

    private RetentionPolicy() {
    }

    /**
     * O plano de retenção de um tenant num instante.
     *
     * @param rawBefore          descartar série bruta anterior a este instante
     * @param hourlyMonthsBefore descartar partições horárias de meses anteriores a este mês
     * @param personalDataBefore descartar curva horária (dado pessoal) anterior a este instante
     * @param rollupPolicy       o valor de {@code tele.rollup_policy} do tenant
     */
    public record RetentionPlan(Instant rawBefore, YearMonth hourlyMonthsBefore,
                                Instant personalDataBefore, String rollupPolicy) {
    }

    public static RetentionPlan plan(TelemetryParameters parameters, UUID tenantId, Instant now) {
        TelemetryInput.notNull(parameters, "parameters");
        TelemetryInput.notNull(tenantId, "tenantId");
        TelemetryInput.notNull(now, "now");
        int rawDays = parameters.retentionRawDays(tenantId);
        int hourlyMonths = parameters.retentionHourlyMonths(tenantId);
        int amiDays = parameters.amiRetentionDays(tenantId);
        TelemetryInput.require(rawDays > 0, MeteringErrorCode.INVALID_INPUT,
                "tele.retention_raw_days precisa ser positivo");
        TelemetryInput.require(hourlyMonths > 0, MeteringErrorCode.INVALID_INPUT,
                "tele.retention_hourly_months precisa ser positivo");
        TelemetryInput.require(amiDays > 0, MeteringErrorCode.INVALID_INPUT,
                "ami.retention_days precisa ser positivo");
        String rollup = parameters.rollupPolicy(tenantId);
        TelemetryInput.require(rollup != null && !rollup.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "tele.rollup_policy é obrigatório");
        return new RetentionPlan(
                now.minusSeconds(rawDays * 86_400L),
                YearMonth.from(now.atOffset(ZoneOffset.UTC)).minusMonths(hourlyMonths),
                now.minusSeconds(amiDays * 86_400L),
                rollup);
    }
}
