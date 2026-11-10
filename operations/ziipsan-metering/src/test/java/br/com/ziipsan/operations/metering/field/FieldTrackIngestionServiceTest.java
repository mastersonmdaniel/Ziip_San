package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.api.ApiException;
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
 * Sincronizacao da trilha GPS (ED-20 / P-MOB-GPS-01): portao do aparelho,
 * ingestao composta do nucleo ANTES da persistencia (P-ED20-02) e recibo
 * por ponto com o motivo nomeado da recusa — tudo sem banco, sobre a
 * {@link InMemoryTrackStore} de ensaio.
 */
class FieldTrackIngestionServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T11:00:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID WORKER = UUID.randomUUID();
    private static final UUID TEAM = UUID.randomUUID();
    private static final Tracking.Shift SHIFT = new Tracking.Shift(T0, T0.plusSeconds(8 * 3600));

    private boolean gateOpen = true;
    private boolean notice = true;
    private boolean trackOnlyInShift = true;
    private InMemoryTrackStore store;
    private FieldTrackIngestionService service;

    @BeforeEach
    void setUp() {
        FieldDeviceGate gate = new FieldDeviceGate() {
            @Override
            public void assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now) {
                if (!gateOpen) {
                    throw new ApiException.Forbidden("Aparelho nao autorizado a sincronizar.");
                }
            }

            @Override
            public SyncDirective directiveFor(UUID tenantId, UUID deviceId) {
                return gateOpen ? SyncDirective.NONE : SyncDirective.BLOCK;
            }
        };
        FieldTrackContextProvider contexts = new FieldTrackContextProvider() {
            @Override
            public FieldTrackContext contextFor(UUID tenantId, UUID workerId) {
                return new FieldTrackContext(SHIFT, notice, trackOnlyInShift,
                        5.0, Duration.ofMinutes(5), 30);
            }

            @Override
            public FieldTrackPanelContext panelContextFor(UUID tenantId) {
                return new FieldTrackPanelContext(Tracking.AccessRole.SUPERVISOR, List.of(WORKER));
            }
        };
        store = new InMemoryTrackStore();
        service = new FieldTrackIngestionService(gate, contexts, store);
    }

    private Tracking.TrackPoint point(long secondsFromStart, double latitude, double longitude) {
        return new Tracking.TrackPoint(WORKER, TEAM, T0.plusSeconds(secondsFromStart), latitude, longitude);
    }

    private List<Tracking.TrackPoint> stored() {
        return store.points(TENANT, WORKER, T0.minusSeconds(3600), T0.plusSeconds(24 * 3600));
    }

    @Test
    @DisplayName("ponto dentro da jornada, com aviso registado, e aceite e persistido")
    void acceptsPointInsideShiftWithNotice() {
        List<FieldTrackReceipt> receipts = service.ingestBatch(TENANT, DEVICE,
                List.of(point(600, -26.0, -48.7)), NOW);
        assertThat(receipts).hasSize(1);
        assertThat(receipts.get(0).accepted()).isTrue();
        assertThat(receipts.get(0).refusalReason()).isNull();
        assertThat(receipts.get(0).workerId()).isEqualTo(WORKER);
        assertThat(stored()).hasSize(1);
    }

    @Test
    @DisplayName("sem aviso registado ao empregado, o ponto e recusado com o motivo do nucleo e nada persiste")
    void refusesPointWithoutNotice() {
        notice = false;
        List<FieldTrackReceipt> receipts = service.ingestBatch(TENANT, DEVICE,
                List.of(point(600, -26.0, -48.7)), NOW);
        assertThat(receipts).hasSize(1);
        assertThat(receipts.get(0).accepted()).isFalse();
        assertThat(receipts.get(0).refusalReason()).contains("aviso de rastreamento ao empregado");
        assertThat(stored()).isEmpty();
    }

    @Test
    @DisplayName("ponto fora da janela da jornada e recusado e nada persiste")
    void refusesPointOutsideShift() {
        List<FieldTrackReceipt> receipts = service.ingestBatch(TENANT, DEVICE,
                List.of(point(9 * 3600, -26.0, -48.7)), NOW);
        assertThat(receipts).hasSize(1);
        assertThat(receipts.get(0).accepted()).isFalse();
        assertThat(receipts.get(0).refusalReason()).contains("fora da jornada");
        assertThat(stored()).isEmpty();
    }

    @Test
    @DisplayName("lote com recibos mistos: o ponto bom nao cai pelo vizinho mau, nem o mau entra pelo bom")
    void mixedBatchReceiptsPerPoint() {
        List<FieldTrackReceipt> receipts = service.ingestBatch(TENANT, DEVICE, List.of(
                point(600, -26.0, -48.7),
                point(9 * 3600, -26.0, -48.7),
                point(1200, -26.0005, -48.7)), NOW);
        assertThat(receipts).hasSize(3);
        assertThat(receipts.get(0).accepted()).isTrue();
        assertThat(receipts.get(1).accepted()).isFalse();
        assertThat(receipts.get(1).refusalReason()).contains("fora da jornada");
        assertThat(receipts.get(2).accepted()).isTrue();
        assertThat(stored()).hasSize(2);
    }

    @Test
    @DisplayName("com a trava field.track_only_in_shift desligada na configuracao, o nucleo recusa cada ponto")
    void refusesWhenTrackOnlyInShiftConfiguredOff() {
        trackOnlyInShift = false;
        List<FieldTrackReceipt> receipts = service.ingestBatch(TENANT, DEVICE,
                List.of(point(600, -26.0, -48.7)), NOW);
        assertThat(receipts.get(0).accepted()).isFalse();
        assertThat(receipts.get(0).refusalReason()).contains("travado em verdadeiro");
        assertThat(stored()).isEmpty();
    }

    @Test
    @DisplayName("aparelho nao autorizado (portao fechado) nao ingere ponto nenhum do lote")
    void gateClosedBlocksWholeBatch() {
        gateOpen = false;
        assertThatThrownBy(() -> service.ingestBatch(TENANT, DEVICE,
                List.of(point(600, -26.0, -48.7)), NOW))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(stored()).isEmpty();
    }
}
