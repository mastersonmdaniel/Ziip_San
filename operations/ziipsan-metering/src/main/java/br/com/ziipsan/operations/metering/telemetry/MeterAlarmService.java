package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import br.com.ziipsan.operations.metering.domain.MeterAlarm;
import br.com.ziipsan.operations.metering.repository.MeterAlarmRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Avaliação de alarmes de um ponto (ED-17): detecta sobre a série horária
 * ({@link TelemetryAlarmRule}), persiste cada alarme novo (sem duplicar
 * alarme aberto do mesmo tipo no ponto) e despacha conforme a configuração
 * de {@code ami.alert_thresholds}: abre OS pela porta de OS, avisa o usuário
 * pelo canal da P3.5, os dois, ou nenhum — sem ação configurada, o alarme
 * fica persistido com a ação nula.
 *
 * <p>Na mesma avaliação saem os alertas ao consumidor da curva: consumo
 * contínuo por 24 horas e pico, pelo canal da P3.5, junto do alerta
 * preventivo de consumo anormal dela. <b>Decisão registrada:</b> o alerta
 * ao consumidor é emitido uma vez por avaliação em que a condição aparece
 * sobre dados novos (cada lote aceito reavalia a janela); quem segura
 * repetição para o mesmo consumidor é o canal da P3.5, com a política de
 * supressão e as preferências do tenant — os alarmes do medidor, ao
 * contrário, são persistidos e deduplicados pelo alarme aberto.
 *
 * <p>Classe simples, sem anotação, como os demais serviços do projeto.
 */
public class MeterAlarmService {

    /** Código do modelo do aviso de alarme do medidor (catálogo de modelos da P2.30). */
    public static final String METER_ALARM_ALERT = "telemetry.meter-alarm";
    /** Código do modelo do alerta de consumo contínuo por 24 horas. */
    public static final String CONTINUOUS_ALERT = "telemetry.continuous-consumption";
    /** Código do modelo do alerta de pico. */
    public static final String PEAK_ALERT = "telemetry.peak-consumption";

    private final MeterAlarmRepository alarms;
    private final ServiceOrderOpener serviceOrders;
    private final TelemetryAlertNotifier notifier;
    private final TelemetryParameters parameters;
    private final Clock clock;

    public MeterAlarmService(MeterAlarmRepository alarms, ServiceOrderOpener serviceOrders,
                             TelemetryAlertNotifier notifier, TelemetryParameters parameters, Clock clock) {
        this.alarms = Objects.requireNonNull(alarms, "O repositório de alarmes é obrigatório");
        this.serviceOrders = Objects.requireNonNull(serviceOrders, "A porta de OS é obrigatória");
        this.notifier = Objects.requireNonNull(notifier, "A porta de aviso é obrigatória");
        this.parameters = Objects.requireNonNull(parameters, "Os parâmetros de telemetria são obrigatórios");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório");
    }

    /** Resultado da avaliação: os alarmes criados e os códigos de alerta ao consumidor disparados. */
    public record Evaluation(List<MeterAlarm> createdAlarms, List<String> consumerAlerts) {
    }

    public Evaluation evaluate(UUID tenantId, MeasuringPoint point, List<HourlySample> samples) {
        Objects.requireNonNull(point, "O ponto de medição é obrigatório");
        AmiAlertThresholds thresholds = AmiAlertThresholds.parse(parameters.amiAlertThresholdsJson(tenantId));
        List<TelemetryAlarmRule.Detected> detected = TelemetryAlarmRule.detect(samples,
                parameters.alarmLeakHours(tenantId), parameters.alarmReverseFlowLitres(tenantId));

        List<MeterAlarm> created = new ArrayList<>();
        for (TelemetryAlarmRule.Detected alarm : detected) {
            if (alarms.existsByTenantIdAndMeasuringPointIdAndKindAndStatus(
                    tenantId, point.getId(), alarm.kind(), MeterAlarm.Status.OPEN)) {
                continue;
            }
            MeterAlarm entity = new MeterAlarm();
            entity.setTenantId(tenantId);
            entity.setMeasuringPointId(point.getId());
            entity.setUnitId(point.getUnitId());
            entity.setKind(alarm.kind());
            entity.setStatus(MeterAlarm.Status.OPEN);
            entity.setRaisedAt(alarm.at());
            entity.setDetail(alarm.detailJson());
            entity.setLargeConsumer(point.isLargeConsumer());
            thresholds.actionFor(alarm.kind()).ifPresent(action -> {
                entity.setAction(action);
                if (action == AlarmAction.OPEN_SERVICE_ORDER || action == AlarmAction.BOTH) {
                    entity.setServiceOrderId(serviceOrders.openForAlarm(
                            tenantId, point.getUnitId(), alarm.kind(), alarm.detailJson()));
                }
                if (action == AlarmAction.NOTIFY_CUSTOMER || action == AlarmAction.BOTH) {
                    notifier.alertCustomer(tenantId, point.getUnitId(), METER_ALARM_ALERT,
                            Map.of("alarmKind", alarm.kind().name(), "detail", alarm.detailJson()));
                    entity.setNotifiedAt(OffsetDateTime.now(clock));
                }
            });
            created.add(alarms.save(entity));
        }

        List<String> consumerAlerts = new ArrayList<>();
        if (ContinuousConsumptionRule.hasContinuous24Hours(samples)) {
            notifier.alertCustomer(tenantId, point.getUnitId(), CONTINUOUS_ALERT,
                    Map.of("continuousHours", ContinuousConsumptionRule.CONTINUOUS_HOURS));
            consumerAlerts.add(CONTINUOUS_ALERT);
        }
        ContinuousConsumptionRule.peakAbove(samples, thresholds.peakHourlyLitres()).ifPresent(peak -> {
            notifier.alertCustomer(tenantId, point.getUnitId(), PEAK_ALERT,
                    Map.of("peakHourlyLitres", peak.volumeLitres(),
                            "hourStart", peak.hourStart().toString()));
            consumerAlerts.add(PEAK_ALERT);
        });
        return new Evaluation(List.copyOf(created), List.copyOf(consumerAlerts));
    }

    /** O detalhe de um alarme como mapa, para quem exibe a trilha (leitura pela classe {@code Json} do core). */
    public static Map<String, Object> detailOf(MeterAlarm alarm) {
        return Json.readMap(alarm.getDetail());
    }
}
