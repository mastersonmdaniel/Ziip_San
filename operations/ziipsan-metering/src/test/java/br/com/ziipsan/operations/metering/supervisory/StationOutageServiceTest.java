package br.com.ziipsan.operations.metering.supervisory;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.StationOutage;
import br.com.ziipsan.operations.metering.repository.StationOutageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** O registro manual de paradas da ETA da ED-17 (o diário de operação é da ED-87). */
class StationOutageServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OUTAGE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-07T02:00:00-03:00");

    private StationOutageRepository outages;
    private StationOutageService service;

    @BeforeEach
    void setUp() {
        outages = mock(StationOutageRepository.class);
        service = new StationOutageService(outages);
        when(outages.save(any(StationOutage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("o registro manual grava a parada aberta com origem MANUAL")
    void manualRegistrationOpensOutage() {
        when(outages.findByTenantIdAndStationCodeAndEndedAtIsNull(TENANT, "ETA-CENTRAL"))
                .thenReturn(Optional.empty());

        StationOutage outage = service.registerManual(TENANT, "ETA-CENTRAL", START,
                "manutenção da bomba de recalque", UUID.randomUUID());

        assertThat(outage.getSource()).isEqualTo(StationOutage.Source.MANUAL);
        assertThat(outage.getEndedAt()).isNull();
        assertThat(outage.getStationCode()).isEqualTo("ETA-CENTRAL");
    }

    @Test
    @DisplayName("estação com parada aberta não aceita segunda parada")
    void secondOpenOutageConflicts() {
        StationOutage open = new StationOutage();
        open.setId(OUTAGE);
        when(outages.findByTenantIdAndStationCodeAndEndedAtIsNull(TENANT, "ETA-CENTRAL"))
                .thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.registerManual(TENANT, "ETA-CENTRAL", START, "outra parada",
                UUID.randomUUID())).isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("fechar a parada grava o fim; fechar de novo é conflito")
    void closeRecordsEnd() {
        StationOutage open = new StationOutage();
        open.setId(OUTAGE);
        open.setTenantId(TENANT);
        open.setStationCode("ETA-CENTRAL");
        open.setStartedAt(START);
        when(outages.findByTenantIdAndId(TENANT, OUTAGE)).thenReturn(Optional.of(open));

        StationOutage closed = service.close(TENANT, OUTAGE, START.plusHours(3));
        assertThat(closed.getEndedAt()).isEqualTo(START.plusHours(3));
        assertThatThrownBy(() -> service.close(TENANT, OUTAGE, START.plusHours(4)))
                .isInstanceOf(ApiException.Conflict.class);
    }
}
