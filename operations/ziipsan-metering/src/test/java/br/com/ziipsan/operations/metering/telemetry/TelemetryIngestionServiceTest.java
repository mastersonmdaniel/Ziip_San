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
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A ingestão da ED-17: escopo da P2.26 em toda entrada, idempotência das
 * leituras AMR no escopo (tenant, mês) e das amostras horárias, coerência
 * de canal, unidade e fuso na entrada, ponto sem base legal sem curva
 * horária, avaliação sobre a janela persistida e o arquivo AMR resolvendo
 * ponto e canal pelos códigos.
 */
class TelemetryIngestionServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CLIENT = "cliente-ami";
    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-10T08:00:00-03:00");

    private MeasuringPointRepository points;
    private TelemetryChannelRepository channels;
    private TelemetryReadingRepository readings;
    private TelemetryHourlyRepository hourly;
    private TelemetryVeeService vee;
    private MeterAlarmService alarmService;
    private IntegrationCredentialChecker credentials;
    private TelemetryIngestionService service;
    private MeasuringPoint point;

    @BeforeEach
    void setUp() {
        points = mock(MeasuringPointRepository.class);
        channels = mock(TelemetryChannelRepository.class);
        readings = mock(TelemetryReadingRepository.class);
        hourly = mock(TelemetryHourlyRepository.class);
        vee = mock(TelemetryVeeService.class);
        alarmService = mock(MeterAlarmService.class);
        credentials = mock(IntegrationCredentialChecker.class);
        TelemetryParameters parameters = mock(TelemetryParameters.class);
        when(parameters.alarmLeakHours(TENANT)).thenReturn(3);
        service = new TelemetryIngestionService(points, channels, readings, hourly, vee, alarmService,
                credentials, parameters, CLOCK);

        when(credentials.hasScope(TENANT, CLIENT, TelemetryScopes.TELEMETRY_WRITE)).thenReturn(true);
        point = new MeasuringPoint();
        point.setId(POINT);
        point.setTenantId(TENANT);
        point.setCode("PTO-001");
        point.setUnitId(UNIT);
        point.setKind(MeasurementKind.WATER);
        point.setTimezoneId("America/Sao_Paulo");
        point.setLegalBasis("EXECUCAO_DO_CONTRATO");
        point.setActive(true);
        when(points.findByTenantIdAndId(TENANT, POINT)).thenReturn(Optional.of(point));
        when(points.findByTenantIdAndCode(TENANT, "PTO-001")).thenReturn(Optional.of(point));

        TelemetryChannel channel = new TelemetryChannel();
        channel.setId(CHANNEL);
        channel.setTenantId(TENANT);
        channel.setMeasuringPointId(POINT);
        channel.setCode("VOL");
        channel.setActive(true);
        when(channels.findByTenantIdAndId(TENANT, CHANNEL)).thenReturn(Optional.of(channel));
        when(channels.findByTenantIdAndMeasuringPointIdAndCode(TENANT, POINT, "VOL"))
                .thenReturn(Optional.of(channel));

        when(readings.save(any(TelemetryReading.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hourly.save(any(TelemetryHourly.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(alarmService.evaluate(any(), any(), any()))
                .thenReturn(new MeterAlarmService.Evaluation(List.of(), List.of()));
    }

    @Test
    @DisplayName("credencial sem telemetry:write não ingere nada")
    void missingScopeIsForbidden() {
        when(credentials.hasScope(TENANT, CLIENT, TelemetryScopes.TELEMETRY_WRITE)).thenReturn(false);

        assertThatThrownBy(() -> service.ingestReadings(TENANT, CLIENT, List.of()))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> service.ingestHourly(TENANT, CLIENT, List.of()))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(readings, never()).save(any());
        verify(hourly, never()).save(any());
    }

    @Test
    @DisplayName("leitura AMR reenviada com a mesma referência no mesmo mês não duplica e não repete a VEE")
    void amrResendDoesNotDuplicate() {
        AmrReading input = new AmrReading(POINT, CHANNEL, AT, 1050, DataState.MEASURED, "AMR-0001");
        when(readings.existsByTenantIdAndReferenceMonthAndIdempotencyKey(
                TENANT, LocalDate.of(2026, 11, 1), "AMR-0001")).thenReturn(false, true);

        TelemetryIngestionService.IngestOutcome first = service.ingestReadings(TENANT, CLIENT, List.of(input));
        TelemetryIngestionService.IngestOutcome second = service.ingestReadings(TENANT, CLIENT, List.of(input));

        assertThat(first.accepted()).isEqualTo(1);
        assertThat(second.accepted()).isEqualTo(0);
        assertThat(second.duplicates()).isEqualTo(1);
        verify(readings).save(any(TelemetryReading.class));
        verify(vee).assess(eq(TENANT), any(TelemetryReading.class));
    }

    @Test
    @DisplayName("a mesma referência externa em OUTRO mês é outra leitura (o escopo da idempotência é o mês)")
    void sameReferenceInAnotherMonthIsNewReading() {
        AmrReading december = new AmrReading(POINT, CHANNEL,
                OffsetDateTime.parse("2026-12-10T08:00:00-03:00"), 1100, DataState.MEASURED, "AMR-0001");
        when(readings.existsByTenantIdAndReferenceMonthAndIdempotencyKey(
                TENANT, LocalDate.of(2026, 12, 1), "AMR-0001")).thenReturn(false);

        TelemetryIngestionService.IngestOutcome outcome = service.ingestReadings(TENANT, CLIENT, List.of(december));

        assertThat(outcome.accepted()).isEqualTo(1);
        assertThat(outcome.duplicates()).isEqualTo(0);
    }

    @Test
    @DisplayName("canal de outro ponto e instante com fuso divergente do ponto são recusados na entrada")
    void foreignChannelAndWrongZoneAreRefused() {
        UUID otherPoint = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        TelemetryChannel foreign = new TelemetryChannel();
        foreign.setId(CHANNEL);
        foreign.setMeasuringPointId(otherPoint);
        foreign.setActive(true);
        when(channels.findByTenantIdAndId(TENANT, CHANNEL)).thenReturn(Optional.of(foreign));
        AmrReading input = new AmrReading(POINT, CHANNEL, AT, 1050, DataState.MEASURED, "AMR-0002");

        assertThat(service.ingestReadings(TENANT, CLIENT, List.of(input)).errors()).isEqualTo(1);

        when(channels.findByTenantIdAndId(TENANT, CHANNEL)).thenAnswer(invocation -> {
            TelemetryChannel own = new TelemetryChannel();
            own.setId(CHANNEL);
            own.setMeasuringPointId(POINT);
            own.setActive(true);
            return Optional.of(own);
        });
        AmrReading utc = new AmrReading(POINT, CHANNEL,
                OffsetDateTime.parse("2026-11-10T08:00:00Z"), 1050, DataState.MEASURED, "AMR-0003");
        assertThat(service.ingestReadings(TENANT, CLIENT, List.of(utc)).errors()).isEqualTo(1);
        verify(readings, never()).save(any());
    }

    @Test
    @DisplayName("amostra horária de ponto sem base legal registrada não entra na curva")
    void hourlyWithoutLegalBasisIsRefused() {
        point.setLegalBasis(null);
        HourlySample sample = new HourlySample(POINT, CHANNEL, UNIT, AT, new BigDecimal("10"),
                BigDecimal.ZERO, DataState.MEASURED, Set.of());

        TelemetryIngestionService.IngestOutcome outcome = service.ingestHourly(TENANT, CLIENT, List.of(sample));

        assertThat(outcome.accepted()).isEqualTo(0);
        assertThat(outcome.errors()).isEqualTo(1);
        verify(hourly, never()).save(any());
    }

    @Test
    @DisplayName("amostra com unidade diferente da do ponto é recusada")
    void hourlyWithForeignUnitIsRefused() {
        HourlySample sample = new HourlySample(POINT, CHANNEL,
                UUID.fromString("00000000-0000-0000-0000-0000000000ff"), AT, new BigDecimal("10"),
                BigDecimal.ZERO, DataState.MEASURED, Set.of());

        TelemetryIngestionService.IngestOutcome outcome = service.ingestHourly(TENANT, CLIENT, List.of(sample));

        assertThat(outcome.errors()).isEqualTo(1);
        verify(hourly, never()).save(any());
    }

    @Test
    @DisplayName("a mesma hora do mesmo canal não duplica na curva")
    void hourlyDuplicateHourDoesNotDuplicate() {
        HourlySample sample = new HourlySample(POINT, CHANNEL, UNIT, AT, new BigDecimal("10"),
                BigDecimal.ZERO, DataState.MEASURED, Set.of());
        when(hourly.existsByTenantIdAndMeasuringPointIdAndChannelIdAndHourStart(TENANT, POINT, CHANNEL, AT))
                .thenReturn(true);

        TelemetryIngestionService.IngestOutcome outcome = service.ingestHourly(TENANT, CLIENT, List.of(sample));

        assertThat(outcome.duplicates()).isEqualTo(1);
        assertThat(outcome.accepted()).isEqualTo(0);
        verify(hourly, never()).save(any());
    }

    @Test
    @DisplayName("a avaliação de alarmes enxerga a janela persistida junto do lote (sequência que cruza lotes)")
    void evaluationSeesPersistedWindow() {
        TelemetryHourly persisted = new TelemetryHourly();
        persisted.setTenantId(TENANT);
        persisted.setMeasuringPointId(POINT);
        persisted.setChannelId(CHANNEL);
        persisted.setUnitId(UNIT);
        persisted.setHourStart(AT.minusHours(1));
        persisted.setVolumeLitres(new BigDecimal("10"));
        persisted.setDataState(DataState.MEASURED);
        when(hourly.findByTenantIdAndMeasuringPointIdAndHourStartBetweenOrderByHourStartAsc(
                eq(TENANT), eq(POINT), any(), any())).thenReturn(List.of(persisted));
        HourlySample sample = new HourlySample(POINT, CHANNEL, UNIT, AT, new BigDecimal("10"),
                BigDecimal.ZERO, DataState.MEASURED, Set.of());

        service.ingestHourly(TENANT, CLIENT, List.of(sample));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HourlySample>> captor = ArgumentCaptor.forClass(List.class);
        verify(alarmService).evaluate(eq(TENANT), eq(point), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).hourStart()).isEqualTo(AT.minusHours(1));
        assertThat(captor.getValue().get(1).hourStart()).isEqualTo(AT);
    }

    @Test
    @DisplayName("arquivo AMR no leiaute resolve ponto e canal pelos códigos e ingere a linha")
    void amrFileImportResolvesCodes() {
        when(readings.existsByTenantIdAndReferenceMonthAndIdempotencyKey(any(), any(), anyString()))
                .thenReturn(false);
        String content = AmrFileLayout.HEADER + "\n"
                + "AMR-0001;PTO-001;VOL;2026-11-10T08:00:00-03:00;1050;MEASURED\n"
                + "AMR-0002;PTO-999;VOL;2026-11-10T08:00:00-03:00;10;MEASURED\n";

        TelemetryIngestionService.IngestOutcome outcome = service.importAmrFile(TENANT, CLIENT, content);

        assertThat(outcome.accepted()).isEqualTo(1);
        assertThat(outcome.errors()).isEqualTo(1);
        verify(readings).save(any(TelemetryReading.class));
    }
}
