package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.domain.TelemetryReading;
import br.com.ziipsan.operations.metering.repository.MeasuringPointRepository;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.repository.TelemetryReadingRepository;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import br.com.ziipsan.operations.metering.rules.ReadingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A VEE da ED-17: leitura rejeitada não promove, leitura sem mostrador é
 * estimada pela média e promove como não real, leitura válida promove como
 * real com origem TELEMETRY. Relógio fixo em 2026-11-10T15:00:00Z.
 */
class TelemetryVeeServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID INSTALLATION = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final UUID READING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
    private static final UUID BILLING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a6");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime READ_AT = OffsetDateTime.parse("2026-11-10T08:00:00-03:00");

    private TelemetryReadingRepository readings;
    private MeasuringPointRepository points;
    private MeterReadingRepository billingReadings;
    private ReadingHistoryLookup history;
    private TelemetryVeeService service;

    private TelemetryReading newReading(Integer dial) {
        TelemetryReading reading = new TelemetryReading();
        reading.setId(READING_ID);
        reading.setTenantId(TENANT);
        reading.setMeasuringPointId(POINT);
        reading.setChannelId(CHANNEL);
        reading.setReferenceMonth(YearMonth.of(2026, 11).atDay(1));
        reading.setReadAt(READ_AT);
        reading.setDialReading(dial);
        reading.setDataState(DataState.MEASURED);
        reading.setVeeStatus(VeeStatus.RECEIVED);
        reading.setIdempotencyKey("AMR-0001");
        reading.setReceivedAt(READ_AT);
        return reading;
    }

    @BeforeEach
    void setUp() {
        readings = mock(TelemetryReadingRepository.class);
        points = mock(MeasuringPointRepository.class);
        billingReadings = mock(MeterReadingRepository.class);
        history = mock(ReadingHistoryLookup.class);
        service = new TelemetryVeeService(readings, points, billingReadings, history, CLOCK);

        when(readings.save(any(TelemetryReading.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(history.dialDigits(TENANT, CHANNEL)).thenReturn(Optional.of(5));
        when(history.previousDial(TENANT, POINT)).thenReturn(Optional.of(1000));
        when(history.previousReadAt(TENANT, POINT))
                .thenReturn(Optional.of(OffsetDateTime.parse("2026-10-31T08:00:00-03:00")));
        when(history.averageDailyConsumption(TENANT, POINT)).thenReturn(Optional.of(new BigDecimal("2")));

        MeasuringPoint point = new MeasuringPoint();
        point.setId(POINT);
        point.setTenantId(TENANT);
        point.setUnitId(UNIT);
        point.setInstallationId(INSTALLATION);
        point.setKind(MeasurementKind.WATER);
        when(points.findByTenantIdAndId(TENANT, POINT)).thenReturn(Optional.of(point));
        when(billingReadings.save(any(MeterReading.class))).thenAnswer(invocation -> {
            MeterReading billing = invocation.getArgument(0);
            billing.setId(BILLING_ID);
            return billing;
        });
    }

    @Test
    @DisplayName("leitura rejeitada na VEE (mostrador negativo) guarda os motivos e nunca promove")
    void rejectedReadingNeverPromotes() {
        TelemetryReading reading = newReading(-5);
        when(readings.findByTenantIdAndId(TENANT, READING_ID)).thenReturn(Optional.of(reading));

        TelemetryReading assessed = service.assess(TENANT, reading);

        assertThat(assessed.getVeeStatus()).isEqualTo(VeeStatus.REJECTED);
        assertThat(assessed.getVeeDetail()).contains("DIAL_NEGATIVE");
        assertThatThrownBy(() -> service.promote(TENANT, READING_ID))
                .isInstanceOf(ApiException.Conflict.class);
        verify(billingReadings, never()).save(any());
    }

    @Test
    @DisplayName("leitura futura é rejeitada na VEE")
    void futureReadingIsRejected() {
        TelemetryReading reading = newReading(1010);
        reading.setReadAt(OffsetDateTime.parse("2026-12-01T08:00:00-03:00"));

        TelemetryReading assessed = service.assess(TENANT, reading);

        assertThat(assessed.getVeeStatus()).isEqualTo(VeeStatus.REJECTED);
        assertThat(assessed.getVeeDetail()).contains("READ_AT_IN_FUTURE");
    }

    @Test
    @DisplayName("leitura sem mostrador é estimada pela média diária e promove como leitura não real")
    void missingDialIsEstimatedAndPromotesAsNonReal() {
        TelemetryReading reading = newReading(null);
        when(readings.findByTenantIdAndId(TENANT, READING_ID)).thenReturn(Optional.of(reading));

        TelemetryReading assessed = service.assess(TENANT, reading);

        // anterior 1000 + média 2/dia x 10 dias (31/10 a 10/11) = 1020
        assertThat(assessed.getVeeStatus()).isEqualTo(VeeStatus.ESTIMATED);
        assertThat(assessed.getDataState()).isEqualTo(DataState.ESTIMATED);
        assertThat(assessed.getDialReading()).isEqualTo(1020);

        MeterReading promoted = service.promote(TENANT, READING_ID);

        ArgumentCaptor<MeterReading> captor = ArgumentCaptor.forClass(MeterReading.class);
        verify(billingReadings).save(captor.capture());
        assertThat(promoted.getSource()).isEqualTo(MeterReading.Source.TELEMETRY);
        assertThat(promoted.getReading()).isEqualTo(1020);
        assertThat(promoted.getReadingStatus()).isEqualTo(ReadingStatus.CHANGED);
        assertThat(promoted.getReadingStatus().isReal()).isFalse();
        assertThat(reading.getVeeStatus()).isEqualTo(VeeStatus.PROMOTED);
        assertThat(reading.getPromotedReadingId()).isEqualTo(BILLING_ID);
    }

    @Test
    @DisplayName("sem média nem anterior, a leitura sem mostrador é rejeitada por falta de base")
    void missingDialWithoutBasisIsRejected() {
        when(history.averageDailyConsumption(TENANT, POINT)).thenReturn(Optional.empty());
        TelemetryReading reading = newReading(null);

        TelemetryReading assessed = service.assess(TENANT, reading);

        assertThat(assessed.getVeeStatus()).isEqualTo(VeeStatus.REJECTED);
        assertThat(assessed.getVeeDetail()).contains("MISSING_BASIS_FOR_ESTIMATE");
    }

    @Test
    @DisplayName("leitura válida promove como leitura real com origem TELEMETRY")
    void validReadingPromotesAsReal() {
        TelemetryReading reading = newReading(1050);
        when(readings.findByTenantIdAndId(TENANT, READING_ID)).thenReturn(Optional.of(reading));

        TelemetryReading assessed = service.assess(TENANT, reading);
        assertThat(assessed.getVeeStatus()).isEqualTo(VeeStatus.VALIDATED);

        MeterReading promoted = service.promote(TENANT, READING_ID);
        assertThat(promoted.getReadingStatus()).isEqualTo(ReadingStatus.REALIZED);
        assertThat(promoted.getSource()).isEqualTo(MeterReading.Source.TELEMETRY);
        assertThat(promoted.getUnitId()).isEqualTo(UNIT);
        assertThat(promoted.getInstallationId()).isEqualTo(INSTALLATION);
    }

    @Test
    @DisplayName("edição com valor impossível (negativo ou acima do mostrador) é recusada pela própria VEE")
    void editWithImpossibleValueIsRefused() {
        TelemetryReading reading = newReading(1050);
        reading.setVeeStatus(VeeStatus.VALIDATED);
        when(readings.findByTenantIdAndId(TENANT, READING_ID)).thenReturn(Optional.of(reading));
        UUID actor = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThatThrownBy(() -> service.edit(TENANT, READING_ID, -1, "correção", actor))
                .isInstanceOf(ApiException.Validation.class);
        // Mostrador de 5 dígitos: 100000 excede a capacidade (99999).
        assertThatThrownBy(() -> service.edit(TENANT, READING_ID, 100000, "correção", actor))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(reading.getVeeStatus()).isEqualTo(VeeStatus.VALIDATED);
    }

    @Test
    @DisplayName("edição registrada marca o dado como substituído e guarda a trilha")
    void editMarksSubstitutedWithTrail() {
        TelemetryReading reading = newReading(1050);
        reading.setVeeStatus(VeeStatus.VALIDATED);
        when(readings.findByTenantIdAndId(TENANT, READING_ID)).thenReturn(Optional.of(reading));

        TelemetryReading edited = service.edit(TENANT, READING_ID, 1060, "leitura conferida em campo",
                UUID.fromString("22222222-2222-2222-2222-222222222222"));

        assertThat(edited.getDialReading()).isEqualTo(1060);
        assertThat(edited.getDataState()).isEqualTo(DataState.SUBSTITUTED);
        assertThat(edited.getVeeStatus()).isEqualTo(VeeStatus.EDITED);
        assertThat(edited.getVeeDetail()).contains("1050").contains("leitura conferida em campo");
    }
}
