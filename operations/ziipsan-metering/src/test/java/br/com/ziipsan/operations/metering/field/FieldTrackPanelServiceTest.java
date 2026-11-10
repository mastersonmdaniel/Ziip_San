package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.GpsPanel;
import br.com.ziipsan.core.fieldos.Tracking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Painel de acompanhamento da trilha (ED-20 / P-MOB-GPS-01): o gate de
 * chefia e o do proprio nucleo e o painel monta trilha + resumo sobre os
 * pontos que entraram pela ingestao — sem banco, sobre a
 * {@link InMemoryTrackStore} de ensaio.
 */
class FieldTrackPanelServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T11:00:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID WORKER = UUID.randomUUID();
    private static final UUID TEAM = UUID.randomUUID();
    private static final Tracking.Shift SHIFT = new Tracking.Shift(T0, T0.plusSeconds(8 * 3600));

    private Tracking.AccessRole role = Tracking.AccessRole.SUPERVISOR;
    private InMemoryTrackStore store;
    private FieldTrackIngestionService ingestion;
    private FieldTrackPanelService panelService;

    @BeforeEach
    void setUp() {
        FieldDeviceGate gate = new FieldDeviceGate() {
            @Override
            public void assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now) {
            }

            @Override
            public SyncDirective directiveFor(UUID tenantId, UUID deviceId) {
                return SyncDirective.NONE;
            }
        };
        FieldTrackContextProvider contexts = new FieldTrackContextProvider() {
            @Override
            public FieldTrackContext contextFor(UUID tenantId, UUID workerId) {
                return new FieldTrackContext(SHIFT, true, true,
                        5.0, Duration.ofMinutes(5), 30);
            }

            @Override
            public FieldTrackPanelContext panelContextFor(UUID tenantId) {
                return new FieldTrackPanelContext(role, List.of(WORKER));
            }
        };
        store = new InMemoryTrackStore();
        ingestion = new FieldTrackIngestionService(gate, contexts, store);
        panelService = new FieldTrackPanelService(contexts, store);
    }

    private Tracking.TrackPoint point(long secondsFromStart, double latitude, double longitude) {
        return new Tracking.TrackPoint(WORKER, TEAM, T0.plusSeconds(secondsFromStart), latitude, longitude);
    }

    private void ingestTrail() {
        // Mesmo desenho do TrackingTest do nucleo: ~111 m em movimento e
        // uma parada de 10 minutos no mesmo lugar.
        List<FieldTrackReceipt> receipts = ingestion.ingestBatch(TENANT, DEVICE, List.of(
                point(0, -26.0000, -48.7000),
                point(60, -26.0010, -48.7000),
                point(660, -26.0010, -48.7000),
                point(720, -26.0010, -48.7000)), NOW);
        assertThat(receipts).allMatch(FieldTrackReceipt::accepted);
    }

    private FieldTrackPanelContext panelContext() {
        return new FieldTrackPanelContext(role, List.of(WORKER));
    }

    @Test
    @DisplayName("painel recusa quem nao e chefia, pelo gate do nucleo")
    void panelRefusesNonSupervisor() {
        ingestTrail();
        role = Tracking.AccessRole.FIELD_WORKER;
        assertThatThrownBy(() -> panelService.panel(TENANT, T0, T0.plusSeconds(3600), panelContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chefia");
        role = Tracking.AccessRole.OTHER;
        assertThatThrownBy(() -> panelService.panel(TENANT, T0, T0.plusSeconds(3600), panelContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chefia");
    }

    @Test
    @DisplayName("painel monta trilha e resumo (distancia, tempo, parada) dos pontos ingeridos")
    void panelBuildsTrackAndSummary() {
        ingestTrail();
        GpsPanel.Panel panel = panelService.panel(TENANT, T0, T0.plusSeconds(3600), panelContext());
        assertThat(panel.tenantId()).isEqualTo(TENANT);
        assertThat(panel.entries()).hasSize(1);
        GpsPanel.PanelEntry entry = panel.entries().get(0);
        assertThat(entry.workerId()).isEqualTo(WORKER);
        assertThat(entry.teamIdOrNull()).isEqualTo(TEAM);
        assertThat(entry.track()).hasSize(4);
        assertThat(entry.summary().distanceMeters()).isBetween(100.0, 130.0);
        assertThat(entry.summary().trackedTime()).isEqualTo(Duration.ofMinutes(12));
        assertThat(entry.summary().stops()).hasSize(1);
        assertThat(entry.summary().stops().get(0).duration()).isEqualTo(Duration.ofMinutes(11));
    }

    @Test
    @DisplayName("janela do painel filtra a trilha: ponto fora da janela consultada nao entra")
    void panelWindowFiltersTrack() {
        ingestTrail();
        GpsPanel.Panel panel = panelService.panel(TENANT, T0.plusSeconds(3600), T0.plusSeconds(7200),
                panelContext());
        assertThat(panel.entries()).hasSize(1);
        assertThat(panel.entries().get(0).track()).isEmpty();
        assertThat(panel.entries().get(0).summary().distanceMeters()).isZero();
    }
}
