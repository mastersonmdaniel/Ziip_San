package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.TariffBracket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Apoio dos testes do pacote field: portas em memoria, sem mocks. */
final class FieldTestSupport {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000071");
    static final UUID TASK = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    static final UUID TARIFF_VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private FieldTestSupport() {
    }

    static FieldTariffSnapshot snapshot(UUID tariffVersion) {
        return new FieldTariffSnapshot(tariffVersion,
                UUID.fromString("00000000-0000-0000-0000-0000000000cc"), 10, new BigDecimal("12.50"),
                List.of(new TariffBracket(10, new BigDecimal("1.50")),
                        new TariffBracket(10, new BigDecimal("2.50")),
                        TariffBracket.open(new BigDecimal("4.00"))),
                true, new BigDecimal("80"), Map.of("tariff", "v2026-01"));
    }

    static FieldBillInput input(UUID tariffVersion, int previous, int current) {
        return new FieldBillInput(TASK, UUID.fromString("00000000-0000-0000-0000-000000000101"),
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                previous, current, 5, 1, snapshot(tariffVersion), List.of(), List.of(),
                LocalDate.of(2026, 12, 10));
    }

    static final class InMemoryStore implements FieldBillPorts.FieldBillStore {
        final Map<String, FieldBillRecord> byKey = new LinkedHashMap<>();
        int saves;

        @Override
        public Optional<FieldBillRecord> findByTask(UUID tenantId, UUID taskId) {
            return Optional.ofNullable(byKey.get(tenantId + "/" + taskId));
        }

        @Override
        public FieldBillRecord save(FieldBillRecord record) {
            saves++;
            byKey.put(record.tenantId() + "/" + record.taskId(), record);
            return record;
        }
    }

    static final class RecordingQueue implements FieldBillPorts.DivergenceQueuePort {
        final List<String> explanations = new ArrayList<>();

        @Override
        public UUID enqueue(FieldBillRecord record, String explanationJson) {
            explanations.add(explanationJson);
            return UUID.fromString("00000000-0000-0000-0000-000000000091");
        }
    }

    static final class RecordingAlerts implements FieldBillPorts.CentralAlertPort {
        int alerts;

        @Override
        public void divergenceDetected(UUID tenantId, UUID taskId, UUID billId, UUID queueItemId) {
            alerts++;
        }
    }

    static final class RecordingCorrections implements FieldBillPorts.BillCorrectionPort {
        FieldBillResult lastCorrectedValues;
        int calls;

        @Override
        public int correctByNewVersion(UUID tenantId, UUID billId, FieldBillResult correctedValues,
                                       UUID requestedBy, UUID approvedBy, String reason) {
            calls++;
            lastCorrectedValues = correctedValues;
            return 2;
        }
    }
}
