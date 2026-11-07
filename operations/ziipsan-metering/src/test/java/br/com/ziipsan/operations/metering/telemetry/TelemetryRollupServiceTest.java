package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.domain.TelemetryHourly;
import br.com.ziipsan.operations.metering.domain.TelemetryHourlyDailySummary;
import br.com.ziipsan.operations.metering.repository.SupervisorySampleRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryHourlyDailySummaryRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryHourlyRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A execução da retenção e dos agregados da ED-17: resumo diário íntegro e cortes por resolução. */
class TelemetryRollupServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    private TelemetryHourlyRepository hourly;
    private TelemetryHourlyDailySummaryRepository summaries;
    private TelemetryReadingRepository readings;
    private SupervisorySampleRepository supervisory;
    private TelemetryRollupService service;

    private static TelemetryHourly hour(OffsetDateTime at, String volume, String reverse) {
        TelemetryHourly hour = new TelemetryHourly();
        hour.setTenantId(TENANT);
        hour.setMeasuringPointId(POINT);
        hour.setChannelId(CHANNEL);
        hour.setUnitId(UNIT);
        hour.setHourStart(at);
        hour.setVolumeLitres(new BigDecimal(volume));
        hour.setReverseVolumeLitres(reverse == null ? null : new BigDecimal(reverse));
        hour.setDataState(DataState.MEASURED);
        return hour;
    }

    @BeforeEach
    void setUp() {
        hourly = mock(TelemetryHourlyRepository.class);
        summaries = mock(TelemetryHourlyDailySummaryRepository.class);
        readings = mock(TelemetryReadingRepository.class);
        supervisory = mock(SupervisorySampleRepository.class);
        TelemetryParameters parameters = mock(TelemetryParameters.class);
        when(parameters.retentionRawDays(TENANT)).thenReturn(30);
        when(parameters.retentionHourlyMonths(TENANT)).thenReturn(13);
        when(parameters.amiRetentionDays(TENANT)).thenReturn(90);
        when(parameters.rollupPolicy(TENANT)).thenReturn("DAILY");
        service = new TelemetryRollupService(hourly, summaries, readings, supervisory, parameters);
        when(summaries.save(any(TelemetryHourlyDailySummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("o resumo diário soma só as horas do dia civil do ponto, no canal certo")
    void rollupDayAggregatesLocalDay() {
        when(hourly.findByTenantIdAndMeasuringPointIdAndHourStartBetweenOrderByHourStartAsc(
                eq(TENANT), eq(POINT), any(), any())).thenReturn(List.of(
                hour(OffsetDateTime.parse("2026-11-09T00:00:00-03:00"), "10", "1"),
                hour(OffsetDateTime.parse("2026-11-09T01:00:00-03:00"), "20", null),
                // A última hora do dia 8 no fuso do ponto (-03:00, o offset que
                // a ingestão confere): é do dia civil 8 e fica fora do resumo do dia 9.
                hour(OffsetDateTime.parse("2026-11-08T23:00:00-03:00"), "99", "9")));
        when(summaries.findByTenantIdAndMeasuringPointIdAndChannelIdAndDay(TENANT, POINT, CHANNEL, DAY))
                .thenReturn(Optional.empty());

        TelemetryHourlyDailySummary summary = service.rollupDay(TENANT, POINT, CHANNEL, DAY);
        assertThat(summary.getTotalVolumeLitres()).isEqualByComparingTo("30");
        assertThat(summary.getTotalReverseLitres()).isEqualByComparingTo("1");
        assertThat(summary.getSamples()).isEqualTo(2);
    }

    @Test
    @DisplayName("a retenção aplica os cortes por resolução e devolve os contadores por série")
    void retentionAppliesCuts() {
        when(hourly.deleteByTenantIdAndBucketMonthLessThan(eq(TENANT), any())).thenReturn(10L);
        when(supervisory.deleteByTenantIdAndBucketMonthLessThan(eq(TENANT), any())).thenReturn(4L);
        when(readings.deleteByTenantIdAndReferenceMonthLessThan(eq(TENANT), any())).thenReturn(7L);

        TelemetryRollupService.RetentionOutcome outcome =
                service.applyRetention(TENANT, Instant.parse("2026-11-10T12:00:00Z"));

        assertThat(outcome.hourlyRemoved()).isEqualTo(10L);
        assertThat(outcome.supervisoryRemoved()).isEqualTo(4L);
        assertThat(outcome.rawRemoved()).isEqualTo(7L);
        // O corte horário efetivo é o mais restritivo: ami.retention_days=90
        // (mês 2026-08) vence tele.retention_hourly_months=13 (mês 2025-10).
        ArgumentCaptor<LocalDate> cut = ArgumentCaptor.forClass(LocalDate.class);
        verify(hourly).deleteByTenantIdAndBucketMonthLessThan(eq(TENANT), cut.capture());
        assertThat(cut.getValue()).isEqualTo(LocalDate.of(2026, 8, 1));
        // A série bruta corta pelo mês de tele.retention_raw_days=30 (mês 2026-10).
        ArgumentCaptor<LocalDate> rawCut = ArgumentCaptor.forClass(LocalDate.class);
        verify(readings).deleteByTenantIdAndReferenceMonthLessThan(eq(TENANT), rawCut.capture());
        assertThat(rawCut.getValue()).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
