package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.MeterReading;
import br.com.ziipsan.operations.metering.repository.MeterReadingRepository;
import br.com.ziipsan.operations.metering.rules.ConsumptionEffect;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import br.com.ziipsan.operations.metering.rules.ReadingEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sincronizacao de campo (ED-18): portao do aparelho, idempotencia por task_id, foto por
 * ocorrencia do catalogo da P2.5 e OCR para a fila de revisao — tudo sem banco.
 */
class FieldSyncServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID INSTALLATION = UUID.randomUUID();
    private static final UUID TASK = UUID.randomUUID();

    private final List<MeterReading> savedReadings = new ArrayList<>();
    private final Map<UUID, FieldSyncReceipt> synced = new HashMap<>();
    private final List<String> reviewReasons = new ArrayList<>();
    private boolean gateOpen = true;
    private FieldSyncService service;

    @BeforeEach
    void setUp() {
        // FieldDeviceGate tem DOIS metodos (assertCanSync + directiveFor): nao e
        // interface funcional, lambda nao compila — classe anonima explicita.
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
        ReadingOccurrenceCatalog catalog = (tenantId, code) -> switch (code) {
            case "NORMAL" -> Optional.of(new ReadingOccurrence("NORMAL", false, true,
                    ReadingEffect.INFORMED, ReadingEffect.PREVIOUS,
                    ConsumptionEffect.NORMAL, ConsumptionEffect.AVERAGE));
            case "VAZAMENTO" -> Optional.of(new ReadingOccurrence("VAZAMENTO", true, true,
                    ReadingEffect.INFORMED, ReadingEffect.PREVIOUS,
                    ConsumptionEffect.NORMAL, ConsumptionEffect.AVERAGE));
            default -> Optional.empty();
        };
        SyncedTaskStore store = new SyncedTaskStore() {
            @Override public Optional<FieldSyncReceipt> find(UUID t, UUID taskId) {
                return Optional.ofNullable(synced.get(taskId));
            }
            @Override public void save(UUID t, FieldSyncReceipt receipt) {
                synced.put(receipt.taskId(), receipt);
            }
        };
        ReadingReviewQueue queue = (t, taskId, reason) -> reviewReasons.add(reason);
        MeterReadingRepository readings = mock(MeterReadingRepository.class);
        when(readings.save(any())).thenAnswer(inv -> {
            MeterReading r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            savedReadings.add(r);
            return r;
        });
        service = new FieldSyncService(gate, catalog, store, queue, readings);
    }

    private FieldReadingSubmission submission(Integer reading, String occurrence, List<String> photos, Integer ocr) {
        return new FieldReadingSubmission(TASK, UNIT, INSTALLATION, MeasurementKind.WATER,
                YearMonth.of(2026, 11), LocalDate.of(2026, 11, 7), reading, occurrence, photos, ocr, NOW);
    }

    private List<FieldSyncReceipt> sync(FieldReadingSubmission s, FieldReadingParameters params) {
        return service.syncRoute(TENANT, DEVICE, List.of(s), params,
                Map.of(INSTALLATION, 5), Map.of(INSTALLATION, 12000), NOW);
    }

    @Test
    @DisplayName("aparelho nao autorizado (portao fechado) nao sincroniza nenhuma tarefa")
    void gateClosedBlocksAll() {
        gateOpen = false;
        assertThatThrownBy(() -> sync(submission(12345, "NORMAL", List.of(), null),
                new FieldReadingParameters(OcrMode.OFF, 0)))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(savedReadings).isEmpty();
    }

    @Test
    @DisplayName("reenvio da mesma task_id nao duplica: devolve o recibo original como duplicado")
    void resendDoesNotDuplicate() {
        var params = new FieldReadingParameters(OcrMode.OFF, 0);
        List<FieldSyncReceipt> first = sync(submission(12345, "NORMAL", List.of(), null), params);
        List<FieldSyncReceipt> second = sync(submission(12345, "NORMAL", List.of(), null), params);
        assertThat(savedReadings).hasSize(1);
        assertThat(second.get(0).duplicate()).isTrue();
        assertThat(second.get(0).readingId()).isEqualTo(first.get(0).readingId());
        assertThat(savedReadings.get(0).getIdempotencyKey()).isEqualTo("field-task:" + TASK);
    }

    @Test
    @DisplayName("ocorrencia fora do catalogo da P2.5 e recusada; ocorrencia com foto exigida sem foto tambem")
    void occurrenceAndPhotoRules() {
        var params = new FieldReadingParameters(OcrMode.OFF, 0);
        assertThatThrownBy(() -> sync(submission(12345, "INEXISTENTE", List.of(), null), params))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> sync(submission(12345, "VAZAMENTO", List.of(), null), params))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(savedReadings).isEmpty();
        sync(submission(12345, "VAZAMENTO", List.of("sha256:abc"), null), params);
        assertThat(savedReadings).hasSize(1);
    }

    @Test
    @DisplayName("divergencia de OCR no modo exigir vai a fila de revisao uma unica vez, mesmo com reenvio")
    void ocrDivergenceGoesToReviewOnce() {
        var params = new FieldReadingParameters(OcrMode.REQUIRE, 0);
        List<FieldSyncReceipt> first = sync(submission(12345, "NORMAL", List.of(), 12945), params);
        assertThat(first.get(0).reviewQueued()).isTrue();
        assertThat(reviewReasons).containsExactly("OCR_DIVERGENCE");
        sync(submission(12345, "NORMAL", List.of(), 12945), params);
        assertThat(reviewReasons).hasSize(1);
        assertThat(savedReadings).hasSize(1);
    }

    @Test
    @DisplayName("com OCR desligado, divergencia nenhuma vai a revisao")
    void ocrOffNeverReviews() {
        var params = new FieldReadingParameters(OcrMode.OFF, 0);
        List<FieldSyncReceipt> receipts = sync(submission(12345, "NORMAL", List.of(), 99999), params);
        assertThat(receipts.get(0).reviewQueued()).isFalse();
        assertThat(reviewReasons).isEmpty();
    }
}
