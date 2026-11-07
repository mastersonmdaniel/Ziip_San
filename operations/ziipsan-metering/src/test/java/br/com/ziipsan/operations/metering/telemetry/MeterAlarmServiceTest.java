package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import br.com.ziipsan.operations.metering.domain.MeterAlarm;
import br.com.ziipsan.operations.metering.repository.MeterAlarmRepository;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O despacho dos alarmes da ED-17 conforme a configuração de
 * {@code ami.alert_thresholds}: abre OS, avisa o usuário, os dois ou nenhum —
 * os quatro desfechos têm teste. Relógio fixo em 2026-11-10T12:00:00Z.
 */
class MeterAlarmServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-10T00:00:00Z");

    private MeterAlarmRepository alarms;
    private ServiceOrderOpener serviceOrders;
    private TelemetryAlertNotifier notifier;
    private TelemetryParameters parameters;
    private MeterAlarmService service;
    private MeasuringPoint point;

    private HourlySample hour(int offsetHours, String volume, Set<MeterAlarmKind> flags) {
        return new HourlySample(POINT, CHANNEL, UNIT, T0.plusHours(offsetHours),
                new BigDecimal(volume), BigDecimal.ZERO, DataState.MEASURED, flags);
    }

    private List<HourlySample> continuousHours(int hours) {
        return java.util.stream.IntStream.range(0, hours)
                .mapToObj(h -> hour(h, "10", Set.of())).toList();
    }

    @BeforeEach
    void setUp() {
        alarms = mock(MeterAlarmRepository.class);
        serviceOrders = mock(ServiceOrderOpener.class);
        notifier = mock(TelemetryAlertNotifier.class);
        parameters = mock(TelemetryParameters.class);
        service = new MeterAlarmService(alarms, serviceOrders, notifier, parameters, CLOCK);
        when(parameters.alarmLeakHours(TENANT)).thenReturn(3);
        when(parameters.alarmReverseFlowLitres(TENANT)).thenReturn(new BigDecimal("50"));
        when(alarms.existsByTenantIdAndMeasuringPointIdAndKindAndStatus(
                any(), any(), any(), any())).thenReturn(false);
        when(alarms.save(any(MeterAlarm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(serviceOrders.openForAlarm(any(), any(), any(), any())).thenReturn(ORDER);

        point = new MeasuringPoint();
        point.setId(POINT);
        point.setTenantId(TENANT);
        point.setUnitId(UNIT);
        point.setKind(MeasurementKind.WATER);
        point.setLargeConsumer(true);
        point.setActive(true);
    }

    @Test
    @DisplayName("configuração OPEN_SERVICE_ORDER: o alarme de vazamento abre OS e não avisa o usuário")
    void leakAlarmOpensServiceOrderOnly() {
        when(parameters.amiAlertThresholdsJson(TENANT)).thenReturn(
                "{\"alarmActions\":{\"CONTINUOUS_FLOW\":\"OPEN_SERVICE_ORDER\"}}");

        MeterAlarmService.Evaluation evaluation = service.evaluate(TENANT, point, continuousHours(3));

        assertThat(evaluation.createdAlarms()).hasSize(1);
        MeterAlarm alarm = evaluation.createdAlarms().get(0);
        assertThat(alarm.getKind()).isEqualTo(MeterAlarmKind.CONTINUOUS_FLOW);
        assertThat(alarm.getAction()).isEqualTo(AlarmAction.OPEN_SERVICE_ORDER);
        assertThat(alarm.getServiceOrderId()).isEqualTo(ORDER);
        assertThat(alarm.getNotifiedAt()).isNull();
        assertThat(alarm.isLargeConsumer()).isTrue();
        verify(serviceOrders).openForAlarm(eq(TENANT), eq(UNIT), eq(MeterAlarmKind.CONTINUOUS_FLOW), any());
        verify(notifier, never()).alertCustomer(any(), any(), eq(MeterAlarmService.METER_ALARM_ALERT), any());
    }

    @Test
    @DisplayName("configuração NOTIFY_CUSTOMER: o alarme do dispositivo avisa o usuário e não abre OS")
    void deviceAlarmNotifiesOnly() {
        when(parameters.amiAlertThresholdsJson(TENANT)).thenReturn(
                "{\"alarmActions\":{\"BATTERY_LOW\":\"NOTIFY_CUSTOMER\"}}");

        MeterAlarmService.Evaluation evaluation = service.evaluate(TENANT, point,
                List.of(hour(0, "0", Set.of(MeterAlarmKind.BATTERY_LOW))));

        assertThat(evaluation.createdAlarms()).hasSize(1);
        MeterAlarm alarm = evaluation.createdAlarms().get(0);
        assertThat(alarm.getKind()).isEqualTo(MeterAlarmKind.BATTERY_LOW);
        assertThat(alarm.getAction()).isEqualTo(AlarmAction.NOTIFY_CUSTOMER);
        assertThat(alarm.getServiceOrderId()).isNull();
        assertThat(alarm.getNotifiedAt()).isNotNull();
        verify(notifier).alertCustomer(eq(TENANT), eq(UNIT), eq(MeterAlarmService.METER_ALARM_ALERT), any());
        verify(serviceOrders, never()).openForAlarm(any(), any(), any(), any());
    }

    @Test
    @DisplayName("configuração BOTH: o alarme de fluxo reverso abre OS e avisa o usuário")
    void reverseFlowAlarmDoesBoth() {
        when(parameters.amiAlertThresholdsJson(TENANT)).thenReturn(
                "{\"alarmActions\":{\"REVERSE_FLOW\":\"BOTH\"}}");
        HourlySample reverse = new HourlySample(POINT, CHANNEL, UNIT, T0,
                BigDecimal.ZERO, new BigDecimal("60"), DataState.MEASURED, Set.of());

        MeterAlarmService.Evaluation evaluation = service.evaluate(TENANT, point, List.of(reverse));

        assertThat(evaluation.createdAlarms()).hasSize(1);
        MeterAlarm alarm = evaluation.createdAlarms().get(0);
        assertThat(alarm.getKind()).isEqualTo(MeterAlarmKind.REVERSE_FLOW);
        assertThat(alarm.getAction()).isEqualTo(AlarmAction.BOTH);
        assertThat(alarm.getServiceOrderId()).isEqualTo(ORDER);
        assertThat(alarm.getNotifiedAt()).isNotNull();
        verify(serviceOrders).openForAlarm(eq(TENANT), eq(UNIT), eq(MeterAlarmKind.REVERSE_FLOW), any());
        verify(notifier).alertCustomer(eq(TENANT), eq(UNIT), eq(MeterAlarmService.METER_ALARM_ALERT), any());
    }

    @Test
    @DisplayName("sem ação configurada: o alarme é persistido com ação nula e nenhum despacho acontece")
    void unconfiguredAlarmIsPersistedWithoutDispatch() {
        when(parameters.amiAlertThresholdsJson(TENANT)).thenReturn("{}");

        MeterAlarmService.Evaluation evaluation = service.evaluate(TENANT, point, continuousHours(3));

        ArgumentCaptor<MeterAlarm> captor = ArgumentCaptor.forClass(MeterAlarm.class);
        verify(alarms).save(captor.capture());
        assertThat(evaluation.createdAlarms()).hasSize(1);
        assertThat(captor.getValue().getAction()).isNull();
        assertThat(captor.getValue().getServiceOrderId()).isNull();
        verify(serviceOrders, never()).openForAlarm(any(), any(), any(), any());
        verify(notifier, never()).alertCustomer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("alarme aberto do mesmo tipo no ponto não é duplicado na avaliação seguinte")
    void openAlarmIsNotDuplicated() {
        when(parameters.amiAlertThresholdsJson(TENANT)).thenReturn(
                "{\"alarmActions\":{\"CONTINUOUS_FLOW\":\"BOTH\"}}");
        when(alarms.existsByTenantIdAndMeasuringPointIdAndKindAndStatus(
                TENANT, POINT, MeterAlarmKind.CONTINUOUS_FLOW, MeterAlarm.Status.OPEN)).thenReturn(true);

        MeterAlarmService.Evaluation evaluation = service.evaluate(TENANT, point, continuousHours(3));

        assertThat(evaluation.createdAlarms()).isEmpty();
        verify(alarms, never()).save(any());
    }
}
