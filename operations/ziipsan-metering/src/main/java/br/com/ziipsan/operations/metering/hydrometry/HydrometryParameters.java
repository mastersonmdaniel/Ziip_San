package br.com.ziipsan.operations.metering.hydrometry;

import java.util.Map;
import java.util.UUID;

/**
 * Porta dos parâmetros {@code meter.*} da hidrometria (ED-14), no mecanismo de parâmetros
 * do tenant do projeto (catálogo core V3, classes a–d). Sem valor vigente, a implementação
 * da porta falha com erro explícito — nunca assume valor em silêncio (padrão das demais
 * portas: {@code TelemetryParameters} da ED-17, {@code BillingParameters} da P2.5).
 * A única exceção é o padrão declarado no aceite: {@code meter.verification_interval_years}
 * tem padrão de fábrica 7 (ver {@link MeterParameterRules#DEFAULT_VERIFICATION_INTERVAL_YEARS}).
 */
public interface HydrometryParameters {
    /** {@code meter.verification_interval_years} — padrão 7, só reduz (1..7). */
    int verificationIntervalYears(UUID tenantId);
    /** {@code meter.replace_by_volume_m3_by_class} — volume totalizado (m³) por classe de exatidão ("1"/"2"). */
    Map<String, Long> replaceByVolumeM3ByClass(UUID tenantId);
    /** {@code meter.sample_plan_pct} — percentual de amostragem do lote para ensaio (0..100). */
    int samplePlanPct(UUID tenantId);
}
