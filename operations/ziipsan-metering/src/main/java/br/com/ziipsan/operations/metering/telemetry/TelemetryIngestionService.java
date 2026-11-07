package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import br.com.ziipsan.operations.metering.domain.TelemetryChannel;
import br.com.ziipsan.operations.metering.domain.TelemetryHourly;
import br.com.ziipsan.operations.metering.domain.TelemetryReading;
import br.com.ziipsan.operations.metering.repository.MeasuringPointRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryChannelRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryHourlyRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryReadingRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ingestão de telemetria (ED-17): a API REST e a importação de arquivo de
 * leituras AMR, sobre a mesma camada de API pública da P2.26 — credencial
 * de integração por tenant e escopo {@code telemetry:write} conferidos em
 * toda entrada. O contrato HTTP (rotas sob {@code /api/v1}, erros em
 * {@code application/problem+json}) está em
 * {@code docs/editais/ED-17-CONTRATO-INGESTAO.md}; este serviço é o núcleo
 * que a camada de API chama, como nos demais módulos do projeto (que não
 * têm controladores).
 *
 * <ul>
 *   <li><b>leituras AMR</b>: entram como {@link TelemetryReading} com a
 *       referência externa como chave de idempotência no escopo (tenant,
 *       mês) do padrão da P2.3 — reenvio no mesmo mês não duplica, e a
 *       mesma referência em outro mês é outra leitura — e passam na hora
 *       pela VEE ({@link TelemetryVeeService#assess});</li>
 *   <li><b>amostras horárias</b>: entram na curva particionada; ponto sem
 *       base legal registrada não recebe amostra horária (o consumo horário
 *       é dado pessoal); a mesma hora do mesmo canal não duplica;</li>
 *   <li><b>coerência conferida na entrada</b> (recusa nomeada, nunca aceite
 *       tácito): o canal existe e é do ponto; a unidade da amostra é a do
 *       ponto; e o fuso do instante confere com o fuso cadastrado do ponto
 *       ({@code timezoneId}) — dispositivo com relógio ou fuso errado não
 *       cai na hora, no mês nem na partição errados em silêncio;</li>
 *   <li><b>alarmes sobre a janela persistida</b>: a avaliação de cada ponto
 *       lê a série já gravada na janela (o maior entre
 *       {@code meter.alarm_leak_hours} e 24 horas, mais uma hora) e a junta
 *       ao lote — uma sequência de fluxo contínuo que cruza dois lotes
 *       dispara do mesmo jeito;</li>
 *   <li><b>arquivo AMR</b>: o leiaute {@link AmrFileLayout} resolve ponto e
 *       canal pelos códigos e delega cada linha à mesma ingestão de
 *       leituras; linha inválida conta como erro, sem derrubar o arquivo.</li>
 * </ul>
 *
 * <p>Classe simples, sem anotação, como os demais serviços do projeto.
 */
public class TelemetryIngestionService {

    private final MeasuringPointRepository points;
    private final TelemetryChannelRepository channels;
    private final TelemetryReadingRepository readings;
    private final TelemetryHourlyRepository hourly;
    private final TelemetryVeeService vee;
    private final MeterAlarmService alarmService;
    private final IntegrationCredentialChecker credentials;
    private final TelemetryParameters parameters;
    private final Clock clock;

    public TelemetryIngestionService(MeasuringPointRepository points, TelemetryChannelRepository channels,
                                     TelemetryReadingRepository readings, TelemetryHourlyRepository hourly,
                                     TelemetryVeeService vee, MeterAlarmService alarmService,
                                     IntegrationCredentialChecker credentials, TelemetryParameters parameters,
                                     Clock clock) {
        this.points = Objects.requireNonNull(points, "O repositório de pontos é obrigatório");
        this.channels = Objects.requireNonNull(channels, "O repositório de canais é obrigatório");
        this.readings = Objects.requireNonNull(readings, "O repositório de leituras de telemetria é obrigatório");
        this.hourly = Objects.requireNonNull(hourly, "O repositório da curva horária é obrigatório");
        this.vee = Objects.requireNonNull(vee, "O serviço de VEE é obrigatório");
        this.alarmService = Objects.requireNonNull(alarmService, "O serviço de alarmes é obrigatório");
        this.credentials = Objects.requireNonNull(credentials, "A porta de credenciais é obrigatória");
        this.parameters = Objects.requireNonNull(parameters, "Os parâmetros de telemetria são obrigatórios");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório");
    }

    /** Resultado de uma ingestão: o que entrou, o que era reenvio e o que foi recusado, por contadores nomeados. */
    public record IngestOutcome(int accepted, int duplicates, int errors, int alarmsCreated,
                                List<String> consumerAlerts) {
    }

    private void requireScope(UUID tenantId, String clientId) {
        if (clientId == null || !credentials.hasScope(tenantId, clientId, TelemetryScopes.TELEMETRY_WRITE)) {
            throw new ApiException.Forbidden(
                    "A credencial de integração não tem o escopo " + TelemetryScopes.TELEMETRY_WRITE + ".");
        }
    }

    /**
     * O fuso do instante confere com o fuso cadastrado do ponto? Um ponto sem
     * {@code timezoneId} válido não confere nada — e nada é presumido: a
     * amostra é recusada pelo chamador.
     */
    private static boolean zoneMatches(MeasuringPoint point, OffsetDateTime instant) {
        if (point.getTimezoneId() == null || point.getTimezoneId().isBlank()) {
            return false;
        }
        ZoneOffset expected = ZoneId.of(point.getTimezoneId()).getRules().getOffset(instant.toInstant());
        return expected.equals(instant.getOffset());
    }

    private boolean channelBelongsToPoint(UUID tenantId, UUID channelId, UUID pointId) {
        return channels.findByTenantIdAndId(tenantId, channelId)
                .map(channel -> channel.getMeasuringPointId().equals(pointId) && channel.isActive())
                .orElse(false);
    }

    /** Ingestão REST de leituras AMR: cada leitura passa pela VEE na entrada. */
    public IngestOutcome ingestReadings(UUID tenantId, String clientId, List<AmrReading> batch) {
        requireScope(tenantId, clientId);
        Objects.requireNonNull(batch, "O lote de leituras é obrigatório");
        int accepted = 0;
        int duplicates = 0;
        int errors = 0;
        for (AmrReading input : batch) {
            MeasuringPoint point = points.findByTenantIdAndId(tenantId, input.measuringPointId()).orElse(null);
            if (point == null || !point.isActive()
                    || !channelBelongsToPoint(tenantId, input.channelId(), input.measuringPointId())
                    || !zoneMatches(point, input.readAt())) {
                errors++;
                continue;
            }
            if (readings.existsByTenantIdAndReferenceMonthAndIdempotencyKey(tenantId,
                    YearMonth.from(input.readAt()).atDay(1), input.externalRef())) {
                duplicates++;
                continue;
            }
            TelemetryReading entity = new TelemetryReading();
            entity.setTenantId(tenantId);
            entity.setMeasuringPointId(input.measuringPointId());
            entity.setChannelId(input.channelId());
            entity.setReferenceMonth(YearMonth.from(input.readAt()).atDay(1));
            entity.setReadAt(input.readAt());
            entity.setDialReading(input.dialReading());
            entity.setDataState(input.state());
            entity.setVeeStatus(VeeStatus.RECEIVED);
            entity.setIdempotencyKey(input.externalRef());
            entity.setReceivedAt(OffsetDateTime.now(clock));
            readings.save(entity);
            vee.assess(tenantId, entity);
            accepted++;
        }
        return new IngestOutcome(accepted, duplicates, errors, 0, List.of());
    }

    /** Ingestão REST de amostras horárias, com avaliação de alarmes sobre a janela persistida do ponto. */
    public IngestOutcome ingestHourly(UUID tenantId, String clientId, List<HourlySample> batch) {
        requireScope(tenantId, clientId);
        Objects.requireNonNull(batch, "O lote de amostras é obrigatório");
        int accepted = 0;
        int duplicates = 0;
        int errors = 0;
        Map<UUID, MeasuringPoint> validatedPoints = new LinkedHashMap<>();
        Map<UUID, List<HourlySample>> acceptedByPoint = new LinkedHashMap<>();
        for (HourlySample sample : batch) {
            MeasuringPoint point = validatedPoints.computeIfAbsent(sample.measuringPointId(),
                    id -> points.findByTenantIdAndId(tenantId, id).orElse(null));
            if (point == null || !point.isActive()
                    || !sample.unitId().equals(point.getUnitId())
                    || !channelBelongsToPoint(tenantId, sample.channelId(), sample.measuringPointId())
                    || !zoneMatches(point, sample.hourStart())) {
                errors++;
                continue;
            }
            if (point.getLegalBasis() == null || point.getLegalBasis().isBlank()) {
                // Consumo horário é dado pessoal: sem base legal registrada no
                // ponto, a amostra não entra — erro nomeado, nunca aceite tácito.
                errors++;
                continue;
            }
            if (hourly.existsByTenantIdAndMeasuringPointIdAndChannelIdAndHourStart(
                    tenantId, sample.measuringPointId(), sample.channelId(), sample.hourStart())) {
                duplicates++;
                continue;
            }
            TelemetryHourly entity = new TelemetryHourly();
            entity.setTenantId(tenantId);
            entity.setMeasuringPointId(sample.measuringPointId());
            entity.setChannelId(sample.channelId());
            entity.setUnitId(sample.unitId());
            entity.setBucketMonth(YearMonth.from(sample.hourStart()).atDay(1));
            entity.setHourStart(sample.hourStart());
            entity.setVolumeLitres(sample.volumeLitres());
            entity.setReverseVolumeLitres(sample.reverseVolumeLitres());
            entity.setDataState(sample.state());
            hourly.save(entity);
            accepted++;
            acceptedByPoint.computeIfAbsent(sample.measuringPointId(), k -> new ArrayList<>()).add(sample);
        }
        int alarmsCreated = 0;
        List<String> consumerAlerts = new ArrayList<>();
        for (Map.Entry<UUID, List<HourlySample>> entry : acceptedByPoint.entrySet()) {
            // A avaliação é POR CANAL: fluxo contínuo e consumo contínuo são
            // da grandeza de um canal; misturar canais do mesmo ponto na
            // mesma série inventaria continuidade que não existe.
            Map<UUID, List<HourlySample>> byChannel = new LinkedHashMap<>();
            for (HourlySample sample : persistedWindow(tenantId, entry.getKey(), entry.getValue())) {
                byChannel.computeIfAbsent(sample.channelId(), k -> new ArrayList<>()).add(sample);
            }
            for (List<HourlySample> series : byChannel.values()) {
                MeterAlarmService.Evaluation evaluation =
                        alarmService.evaluate(tenantId, validatedPoints.get(entry.getKey()), series);
                alarmsCreated += evaluation.createdAlarms().size();
                consumerAlerts.addAll(evaluation.consumerAlerts());
            }
        }
        return new IngestOutcome(accepted, duplicates, errors, alarmsCreated, List.copyOf(consumerAlerts));
    }

    /**
     * A série de avaliação de um ponto: o que já está persistido na janela
     * (o maior entre o limiar de vazamento e as 24h do alerta contínuo, mais
     * uma hora antes da primeira amostra do lote) junto do lote — a amostra
     * do lote vence a persistida na mesma hora e canal, porque é ela que
     * traz as sinalizações do dispositivo.
     */
    private List<HourlySample> persistedWindow(UUID tenantId, UUID pointId, List<HourlySample> batchSamples) {
        int lookbackHours = Math.max(parameters.alarmLeakHours(tenantId),
                ContinuousConsumptionRule.CONTINUOUS_HOURS) + 1;
        OffsetDateTime earliest = batchSamples.stream().map(HourlySample::hourStart)
                .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime latest = batchSamples.stream().map(HourlySample::hourStart)
                .max(Comparator.naturalOrder()).orElseThrow();
        Map<String, HourlySample> byHourAndChannel = new LinkedHashMap<>();
        for (TelemetryHourly persisted : hourly
                .findByTenantIdAndMeasuringPointIdAndHourStartBetweenOrderByHourStartAsc(
                        tenantId, pointId, earliest.minusHours(lookbackHours), latest)) {
            HourlySample sample = new HourlySample(persisted.getMeasuringPointId(), persisted.getChannelId(),
                    persisted.getUnitId(), persisted.getHourStart(), persisted.getVolumeLitres(),
                    persisted.getReverseVolumeLitres(), persisted.getDataState(), Set.of());
            byHourAndChannel.put(persisted.getChannelId() + "|" + persisted.getHourStart().toInstant(), sample);
        }
        for (HourlySample sample : batchSamples) {
            byHourAndChannel.put(sample.channelId() + "|" + sample.hourStart().toInstant(), sample);
        }
        return byHourAndChannel.values().stream()
                .sorted(Comparator.comparing(HourlySample::hourStart)).toList();
    }

    /** Importação de arquivo AMR no leiaute {@link AmrFileLayout}: resolve os códigos e delega à ingestão de leituras. */
    public IngestOutcome importAmrFile(UUID tenantId, String clientId, String content) {
        requireScope(tenantId, clientId);
        List<AmrFileLayout.ParsedLine> lines = AmrFileLayout.parse(content);
        int lineErrors = 0;
        List<AmrReading> batch = new ArrayList<>();
        for (AmrFileLayout.ParsedLine parsed : lines) {
            if (parsed.isError()) {
                lineErrors++;
                continue;
            }
            AmrFileLayout.AmrLine line = parsed.line();
            MeasuringPoint point = points.findByTenantIdAndCode(tenantId, line.pointCode()).orElse(null);
            if (point == null) {
                lineErrors++;
                continue;
            }
            var channel = channels.findByTenantIdAndMeasuringPointIdAndCode(
                    tenantId, point.getId(), line.channelCode());
            if (channel.isEmpty()) {
                lineErrors++;
                continue;
            }
            batch.add(new AmrReading(point.getId(), channel.get().getId(), line.readAt(),
                    line.dialReading(), line.state(), line.externalRef()));
        }
        IngestOutcome outcome = ingestReadings(tenantId, clientId, batch);
        return new IngestOutcome(outcome.accepted(), outcome.duplicates(),
                outcome.errors() + lineErrors, outcome.alarmsCreated(), outcome.consumerAlerts());
    }
}
