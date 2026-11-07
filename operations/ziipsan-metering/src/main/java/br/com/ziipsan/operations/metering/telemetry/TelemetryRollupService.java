package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.domain.TelemetryHourly;
import br.com.ziipsan.operations.metering.domain.TelemetryHourlyDailySummary;
import br.com.ziipsan.operations.metering.repository.SupervisorySampleRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryHourlyDailySummaryRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryHourlyRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryReadingRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A execução da retenção e dos agregados da ED-17 — o outro lado da
 * {@link RetentionPolicy}, que só calcula o plano:
 *
 * <ul>
 *   <li>{@link #rollupDay}: agrega as horas de um dia de um canal na tabela
 *       de resumo ({@code telemetry_hourly_daily_summary}), conforme
 *       {@code tele.rollup_policy} — rodar de novo no mesmo dia atualiza a
 *       mesma linha, nunca duplica o agregado;</li>
 *   <li>{@link #applyRetention}: aplica os cortes do plano por resolução —
 *       a curva horária e as amostras do supervisório saem por mês de
 *       partição anterior ao corte, e a série bruta de leituras idem. O
 *       descarte físico da partição inteira (DROP PARTITION) é DDL do
 *       migrador na integração, como a criação; aqui as linhas vencidas
 *       saem e a partição vazia fica pronta para a rotina do migrador.</li>
 * </ul>
 *
 * Quem agenda (o worker da P1.9, com o tenant explícito na mensagem) é
 * fiação da aplicação; este serviço é o núcleo executável e testável.
 *
 * <p>Classe simples, sem anotação, como os demais serviços do projeto.
 */
public class TelemetryRollupService {

    private final TelemetryHourlyRepository hourly;
    private final TelemetryHourlyDailySummaryRepository summaries;
    private final TelemetryReadingRepository readings;
    private final SupervisorySampleRepository supervisory;
    private final TelemetryParameters parameters;

    public TelemetryRollupService(TelemetryHourlyRepository hourly,
                                  TelemetryHourlyDailySummaryRepository summaries,
                                  TelemetryReadingRepository readings,
                                  SupervisorySampleRepository supervisory,
                                  TelemetryParameters parameters) {
        this.hourly = Objects.requireNonNull(hourly, "O repositório da curva horária é obrigatório");
        this.summaries = Objects.requireNonNull(summaries, "O repositório de resumos é obrigatório");
        this.readings = Objects.requireNonNull(readings, "O repositório de leituras é obrigatório");
        this.supervisory = Objects.requireNonNull(supervisory, "O repositório do supervisório é obrigatório");
        this.parameters = Objects.requireNonNull(parameters, "Os parâmetros de telemetria são obrigatórios");
    }

    /** O desfecho de uma aplicação de retenção: quantas linhas saíram de cada série. */
    public record RetentionOutcome(long hourlyRemoved, long rawRemoved, long supervisoryRemoved,
                                   RetentionPolicy.RetentionPlan plan) {
    }

    /** Agrega um dia de um canal na tabela de resumo; devolve a linha (criada ou atualizada). */
    public TelemetryHourlyDailySummary rollupDay(UUID tenantId, UUID measuringPointId, UUID channelId,
                                                 LocalDate day) {
        // O dia é o dia civil NO FUSO DO PONTO: as amostras carregam o offset
        // do ponto (conferido na ingestão), então o dia de cada amostra é a
        // sua data local. A janela de busca é larga (UTC ± 1 dia) e o filtro
        // pela data local decide — somar por dia UTC misturaria dois dias
        // civis do ponto.
        OffsetDateTime from = day.minusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<TelemetryHourly> hours = hourly
                .findByTenantIdAndMeasuringPointIdAndHourStartBetweenOrderByHourStartAsc(
                        tenantId, measuringPointId, from, to.minusNanos(1)).stream()
                .filter(hour -> hour.getChannelId().equals(channelId))
                .filter(hour -> hour.getHourStart().toLocalDate().equals(day))
                .toList();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal reverse = BigDecimal.ZERO;
        for (TelemetryHourly hour : hours) {
            total = total.add(hour.getVolumeLitres());
            if (hour.getReverseVolumeLitres() != null) {
                reverse = reverse.add(hour.getReverseVolumeLitres());
            }
        }
        TelemetryHourlyDailySummary summary = summaries
                .findByTenantIdAndMeasuringPointIdAndChannelIdAndDay(tenantId, measuringPointId, channelId, day)
                .orElseGet(() -> {
                    TelemetryHourlyDailySummary created = new TelemetryHourlyDailySummary();
                    created.setTenantId(tenantId);
                    created.setMeasuringPointId(measuringPointId);
                    created.setChannelId(channelId);
                    created.setDay(day);
                    return created;
                });
        summary.setTotalVolumeLitres(total);
        summary.setTotalReverseLitres(reverse);
        summary.setSamples(hours.size());
        return summaries.save(summary);
    }

    /** Aplica os cortes de retenção do tenant e devolve os contadores por série. */
    public RetentionOutcome applyRetention(UUID tenantId, Instant now) {
        RetentionPolicy.RetentionPlan plan = RetentionPolicy.plan(parameters, tenantId, now);
        // A curva horária tem DOIS cortes e vale o mais restritivo — o que
        // guarda MENOS tempo: o da resolução (tele.retention_hourly_months)
        // e o do dado pessoal (ami.retention_days, a retenção curta). O mês
        // efetivo é o MAIOR dos dois meses de corte.
        LocalDate hourlyCut = plan.hourlyMonthsBefore().atDay(1);
        LocalDate personalCut = plan.personalDataBefore().atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        if (personalCut.isAfter(hourlyCut)) {
            hourlyCut = personalCut;
        }
        long hourlyRemoved = hourly.deleteByTenantIdAndBucketMonthLessThan(tenantId, hourlyCut);
        long supervisoryRemoved = supervisory.deleteByTenantIdAndBucketMonthLessThan(tenantId, hourlyCut);
        // A série bruta retém por dias (tele.retention_raw_days): o corte em
        // meses de partição é o mês do instante de corte — a partição do mês
        // do corte em si fica, porque ainda contém dias dentro da retenção.
        LocalDate rawCut = plan.rawBefore().atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        long rawRemoved = readings.deleteByTenantIdAndReferenceMonthLessThan(tenantId, rawCut);
        return new RetentionOutcome(hourlyRemoved, rawRemoved, supervisoryRemoved, plan);
    }
}
