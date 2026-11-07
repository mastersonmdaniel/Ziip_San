package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.domain.TelemetryReading;
import br.com.ziipsan.operations.metering.repository.MeasuringPointRepository;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryReadingRepository;
import br.com.ziipsan.operations.metering.rules.ReadingStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A VEE das leituras de telemetria (ED-17) — validação, estimativa e edição
 * ANTES de a leitura virar leitura de faturamento:
 *
 * <ul>
 *   <li>{@link #assess}: valida pela {@link VeeRule}; com violações, a leitura
 *       é <b>rejeitada</b> com os motivos no detalhe e nunca promove; sem
 *       leitura do mostrador, <b>estima</b> pela média diária do ponto
 *       (sem média, rejeita por falta de base — nunca presume valor);</li>
 *   <li>{@link #edit}: a edição troca o valor, marca o dado como
 *       {@code SUBSTITUTED} e guarda valor anterior, motivo e autor no
 *       detalhe — edição de leitura já promovida é recusada;</li>
 *   <li>{@link #promote}: só {@code VALIDATED}, {@code ESTIMATED} ou
 *       {@code EDITED} promovem, criando a {@code meter_readings} com origem
 *       {@code TELEMETRY}. O estado na leitura de faturamento: validada →
 *       {@code REALIZED}; estimada ou editada → {@code CHANGED} (não conta
 *       como leitura real para a cascata, que produz o consumo estimado —
 *       a semântica do próprio {@link ReadingStatus}).</li>
 * </ul>
 *
 * Classe simples, sem anotação, como os demais serviços do projeto.
 */
public class TelemetryVeeService {

    private final TelemetryReadingRepository readings;
    private final MeasuringPointRepository points;
    private final MeterReadingRepository billingReadings;
    private final ReadingHistoryLookup history;
    private final Clock clock;

    public TelemetryVeeService(TelemetryReadingRepository readings, MeasuringPointRepository points,
                               MeterReadingRepository billingReadings, ReadingHistoryLookup history,
                               Clock clock) {
        this.readings = Objects.requireNonNull(readings, "O repositório de leituras de telemetria é obrigatório");
        this.points = Objects.requireNonNull(points, "O repositório de pontos é obrigatório");
        this.billingReadings = Objects.requireNonNull(billingReadings, "O repositório de leituras é obrigatório");
        this.history = Objects.requireNonNull(history, "A porta de histórico é obrigatória");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório");
    }

    /** Valida (ou estima) uma leitura recém-recebida e grava o desfecho da VEE nela. */
    public TelemetryReading assess(UUID tenantId, TelemetryReading reading) {
        Integer previousDial = history.previousDial(tenantId, reading.getMeasuringPointId()).orElse(null);
        var dialDigits = history.dialDigits(tenantId, reading.getChannelId());
        // Sem dígitos de mostrador cadastrados no canal, só a CAPACIDADE não
        // é conferível: valida-se com o teto do domínio (9 dígitos) e
        // descarta-se apenas DIAL_EXCEEDS_CAPACITY. A regressão
        // (DIAL_BELOW_PREVIOUS) continua valendo: com o teto de 9 dígitos, a
        // virada de mostrador nunca se configura para leituras de mostradores
        // menores (a anterior nunca chega à metade alta de um mostrador de 9
        // dígitos), então leitura menor que a anterior é regressão de
        // verdade e rejeita — descartá-la validaria justamente o caso que a
        // VEE existe para barrar.
        List<VeeRule.Violation> violations = dialDigits
                .map(digits -> VeeRule.validate(toInput(reading), previousDial, digits, clock.instant()))
                .orElseGet(() -> VeeRule.validate(toInput(reading), previousDial, 9, clock.instant()).stream()
                        .filter(v -> v != VeeRule.Violation.DIAL_EXCEEDS_CAPACITY)
                        .toList());
        if (!violations.isEmpty()) {
            reading.setVeeStatus(VeeStatus.REJECTED);
            reading.setVeeDetail(Json.write(Map.of("violations",
                    violations.stream().map(Enum::name).toList())));
            return readings.save(reading);
        }
        if (reading.getDialReading() == null) {
            return estimate(tenantId, reading, previousDial);
        }
        reading.setVeeStatus(VeeStatus.VALIDATED);
        reading.setVeeDetail(null);
        return readings.save(reading);
    }

    private TelemetryReading estimate(UUID tenantId, TelemetryReading reading, Integer previousDial) {
        var average = history.averageDailyConsumption(tenantId, reading.getMeasuringPointId());
        var previousAt = history.previousReadAt(tenantId, reading.getMeasuringPointId());
        if (previousDial == null || average.isEmpty() || previousAt.isEmpty()) {
            reading.setVeeStatus(VeeStatus.REJECTED);
            reading.setVeeDetail(Json.write(Map.of("violations", List.of("MISSING_BASIS_FOR_ESTIMATE"))));
            return readings.save(reading);
        }
        // O período da estimativa é o decorrido entre a leitura anterior e
        // esta coleta, em dias civis no fuso de cada instante.
        long days = Math.max(0, ChronoUnit.DAYS.between(
                previousAt.get().toLocalDate(), reading.getReadAt().toLocalDate()));
        int estimated = VeeRule.estimateDial(previousDial, average.get(), days);
        reading.setDialReading(estimated);
        reading.setDataState(DataState.ESTIMATED);
        reading.setVeeStatus(VeeStatus.ESTIMATED);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousDial", previousDial);
        detail.put("averageDailyConsumption", average.get());
        detail.put("days", days);
        detail.put("estimatedDial", estimated);
        reading.setVeeDetail(Json.write(detail));
        return readings.save(reading);
    }

    /** Edição registrada: novo valor, dado {@code SUBSTITUTED}, trilha com anterior, motivo e autor. */
    public TelemetryReading edit(UUID tenantId, UUID readingId, int newDial, String reason, UUID actor) {
        TelemetryReading reading = requireReading(tenantId, readingId);
        if (reading.getVeeStatus() == VeeStatus.PROMOTED) {
            throw new ApiException.Conflict("telemetry-reading-already-promoted",
                    "A leitura já virou leitura de faturamento e não pode ser editada na telemetria.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException.Validation("O motivo da edição é obrigatório.",
                    List.of(new ApiException.FieldError("reason", "required", "O motivo da edição é obrigatório.")),
                    List.of());
        }
        // A edição passa pela mesma validação da coleta: valor negativo ou
        // acima da capacidade do mostrador não vira EDITED — a etapa de
        // edição da VEE não fura a validação que ela mesma aplica.
        var dialDigits = history.dialDigits(tenantId, reading.getChannelId());
        if (newDial < 0 || dialDigits.map(digits -> newDial > VeeRule.dialMaximum(digits)).orElse(false)) {
            throw new ApiException.Validation("O novo valor da leitura é inválido.",
                    List.of(new ApiException.FieldError("newDial", "invalid",
                            "A leitura editada não pode ser negativa nem exceder a capacidade do mostrador.")),
                    List.of());
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousDial", reading.getDialReading());
        detail.put("newDial", newDial);
        detail.put("reason", reason);
        detail.put("editedBy", actor == null ? null : actor.toString());
        reading.setDialReading(newDial);
        reading.setDataState(DataState.SUBSTITUTED);
        reading.setVeeStatus(VeeStatus.EDITED);
        reading.setVeeDetail(Json.write(detail));
        return readings.save(reading);
    }

    /** Promove a leitura aprovada na VEE a leitura de faturamento (origem {@code TELEMETRY}). */
    public MeterReading promote(UUID tenantId, UUID readingId) {
        TelemetryReading reading = requireReading(tenantId, readingId);
        if (reading.getVeeStatus() != VeeStatus.VALIDATED
                && reading.getVeeStatus() != VeeStatus.ESTIMATED
                && reading.getVeeStatus() != VeeStatus.EDITED) {
            throw new ApiException.Conflict("telemetry-reading-not-promotable",
                    "A leitura em situação " + reading.getVeeStatus() + " não pode virar leitura de faturamento.");
        }
        MeasuringPoint point = points.findByTenantIdAndId(tenantId, reading.getMeasuringPointId())
                .orElseThrow(() -> new ApiException.NotFound("Ponto de medição da leitura não encontrado."));
        MeterReading billing = new MeterReading();
        billing.setTenantId(tenantId);
        billing.setUnitId(point.getUnitId());
        billing.setInstallationId(point.getInstallationId());
        billing.setKind(point.getKind());
        YearMonth reference = YearMonth.from(reading.getReadAt());
        billing.setReferenceMonth(reference);
        billing.setReadOn(reading.getReadAt().toLocalDate());
        billing.setReading(reading.getDialReading());
        billing.setReadingStatus(reading.getVeeStatus() == VeeStatus.VALIDATED
                ? ReadingStatus.REALIZED : ReadingStatus.CHANGED);
        billing.setSource(MeterReading.Source.TELEMETRY);
        billing.setCapturedAt(reading.getReadAt());
        billing.setIdempotencyKey(reading.getIdempotencyKey());
        MeterReading saved = billingReadings.save(billing);
        reading.setPromotedReadingId(saved.getId());
        reading.setPromotedReference(reference.atDay(1));
        reading.setVeeStatus(VeeStatus.PROMOTED);
        readings.save(reading);
        return saved;
    }

    private TelemetryReading requireReading(UUID tenantId, UUID readingId) {
        return readings.findByTenantIdAndId(tenantId, readingId)
                .orElseThrow(() -> new ApiException.NotFound("Leitura de telemetria não encontrada."));
    }

    private static AmrReading toInput(TelemetryReading reading) {
        return new AmrReading(reading.getMeasuringPointId(), reading.getChannelId(), reading.getReadAt(),
                reading.getDialReading(), reading.getDataState(), reading.getIdempotencyKey());
    }

    /** O mês de referência de uma leitura, exposto para quem agenda a promoção no ciclo. */
    public static LocalDate referenceMonthOf(OffsetDateTime readAt) {
        return YearMonth.from(readAt).atDay(1);
    }
}
