package br.com.ziipsan.operations.metering.telemetry;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Porta para os parâmetros de telemetria do tenant (ED-17). As chaves são as
 * do aceite da tarefa; nenhuma tem valor de fábrica neste código — ausência
 * de valor vigente é erro explícito da implementação da porta, como nas
 * demais portas de parâmetros do projeto (ver {@code MessagingParameters}
 * da P3.5 e {@code BillingParameters} da P2.5).
 *
 * <ul>
 *   <li>{@code meter.alarm_leak_hours} — horas de fluxo contínuo que disparam
 *       o alarme de vazamento;</li>
 *   <li>{@code meter.alarm_reverse_flow_l} — volume reverso acumulado, em
 *       litros, que dispara o alarme de fluxo reverso;</li>
 *   <li>{@code ami.alert_thresholds} — documento JSON de limiares e ações dos
 *       alarmes (ver {@link AmiAlertThresholds});</li>
 *   <li>{@code meter.telemetry_priority_over_manual} — bifurcação do
 *       ADR-0038: a leitura de telemetria tem prioridade sobre a manual no
 *       ciclo;</li>
 *   <li>{@code meter.hourly_profile_visible_to_customer} — a curva horária é
 *       visível ao usuário;</li>
 *   <li>{@code ami.retention_days} — retenção curta do consumo horário,
 *       tratado como dado pessoal;</li>
 *   <li>{@code tele.retention_raw_days} — retenção da série bruta;</li>
 *   <li>{@code tele.retention_hourly_months} — retenção da série horária;</li>
 *   <li>{@code tele.rollup_policy} — política de agregação da série horária
 *       na tabela de resumo (valor do tenant; este código não inventa
 *       catálogo de valores, só exige que exista).</li>
 * </ul>
 *
 * <p><b>Registro no catálogo:</b> o aceite da ED-17 não reserva versão
 * Flyway para estas chaves e o mapa do plano (§3) também não reserva
 * migration para a ED-17 — as definições no catálogo global de parâmetros
 * entram quando houver versão reservada (pendência declarada no relatório
 * da entrega); esta porta já fixa as chaves.
 */
public interface TelemetryParameters {

    int alarmLeakHours(UUID tenantId);

    BigDecimal alarmReverseFlowLitres(UUID tenantId);

    /** O documento JSON bruto de {@code ami.alert_thresholds}. */
    String amiAlertThresholdsJson(UUID tenantId);

    boolean telemetryPriorityOverManual(UUID tenantId);

    boolean hourlyProfileVisibleToCustomer(UUID tenantId);

    int amiRetentionDays(UUID tenantId);

    int retentionRawDays(UUID tenantId);

    int retentionHourlyMonths(UUID tenantId);

    String rollupPolicy(UUID tenantId);
}
