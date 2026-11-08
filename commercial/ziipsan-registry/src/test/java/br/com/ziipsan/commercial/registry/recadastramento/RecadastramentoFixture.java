package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.quality.BatchOrderGateway;
import br.com.ziipsan.commercial.registry.quality.CorrectionQueueItem;
import br.com.ziipsan.commercial.registry.quality.CorrectionQueueService;
import br.com.ziipsan.commercial.registry.quality.InMemoryCorrectionQueueStore;
import br.com.ziipsan.commercial.registry.quality.QualityFinding;
import br.com.ziipsan.commercial.registry.quality.QualityRuleCode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Massa e fakes da ED-16: fila real da P3.32 sobre store em memoria,
 * gateway de lote falso que reproduz o contrato do motor (abre OS,
 * confirma o efeito uma vez por alvo no encerramento), leitor de
 * cadastro por mapa, aplicador que so registra as chamadas e portao de
 * aparelho configuravel. Nada aqui escreve cadastro de verdade: o
 * aplicador falso e a prova de que o servico so aplica pela porta.
 */
final class RecadastramentoFixture {

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-07T12:00:00Z");
    static final UUID ACTOR = UUID.randomUUID();
    static final UUID APPROVER = UUID.randomUUID();
    static final UUID COLLECTOR = UUID.randomUUID();
    static final UUID DEVICE = UUID.randomUUID();
    static final UUID TENANT = UUID.randomUUID();

    final InMemoryCorrectionQueueStore queueStore = new InMemoryCorrectionQueueStore();
    final InMemoryRecadastramentoStore store = new InMemoryRecadastramentoStore();
    final FakeBatchGateway gateway = new FakeBatchGateway();
    final MapFieldReader reader = new MapFieldReader();
    final RecordingApplier applier = new RecordingApplier();
    final Gate gate = new Gate();
    final CorrectionQueueService queue;
    final RecadastramentoService service;

    RecadastramentoFixture() {
        this.queue = new CorrectionQueueService(queueStore, gateway, customerId -> List.of());
        this.service = new RecadastramentoService(store, queue, gateway, reader, applier, gate);
        this.gateway.handler = service::onBatchEffectConfirmed;
    }

    /** Poe um achado da regra na fila e devolve o item criado. */
    CorrectionQueueItem enqueueUnitFinding(UUID unitId, QualityRuleCode ruleCode) {
        queue.enqueue(List.of(new QualityFinding(ruleCode, ruleCode.defaultSeverity(),
                unitId, null, "achado sintetico " + ruleCode)));
        return queueStore.all().stream()
                .filter(i -> i.ruleCode() == ruleCode && unitId.equals(i.unitId()))
                .reduce((a, b) -> b)
                .orElseThrow();
    }

    CorrectionQueueItem enqueueCustomerFinding(UUID customerId, QualityRuleCode ruleCode) {
        queue.enqueue(List.of(new QualityFinding(ruleCode, ruleCode.defaultSeverity(),
                null, customerId, "achado sintetico " + ruleCode)));
        return queueStore.all().stream()
                .filter(i -> i.ruleCode() == ruleCode && customerId.equals(i.customerId()))
                .reduce((a, b) -> b)
                .orElseThrow();
    }

    /** Abre um lote com um achado por unidade e devolve o lote. */
    RecadastramentoBatch openBatchForUnits(UUID... unitIds) {
        List<UUID> itemIds = new ArrayList<>();
        QualityRuleCode[] rules = QualityRuleCode.values();
        for (int i = 0; i < unitIds.length; i++) {
            itemIds.add(enqueueUnitFinding(unitIds[i], rules[i]).id());
        }
        return service.openBatch(itemIds, ACTOR, NOW).batch();
    }

    FieldCollectionSubmission submission(UUID batchId, UUID unitId, UUID customerId,
                                         String targetType, String field, String value) {
        return submission(batchId, UUID.randomUUID(), unitId, customerId, targetType, field, value, false);
    }

    FieldCollectionSubmission submission(UUID batchId, UUID taskId, UUID unitId, UUID customerId,
                                         String targetType, String field, String value, boolean masked) {
        return new FieldCollectionSubmission(taskId, batchId, DEVICE, COLLECTOR, unitId, customerId,
                targetType, field, value, masked, List.of(), NOW);
    }

    /** Gateway falso: abre a OS do lote e confirma o efeito por alvo no encerramento. */
    static final class FakeBatchGateway implements BatchOrderGateway, BatchOrderCloser {
        CorrectionConfirmationHandler handler;
        final Map<UUID, Map<UUID, List<String>>> orders = new LinkedHashMap<>();
        UUID lastOrderId;
        int executeCount;

        @Override
        public OpenedCorrectionOrder openCorrectionBatch(Map<UUID, List<String>> findingKeysByUnit,
                                                         UUID actorId) {
            UUID orderId = UUID.randomUUID();
            Map<UUID, List<String>> targets = new LinkedHashMap<>();
            findingKeysByUnit.forEach((unit, keys) -> targets.put(unit, List.copyOf(keys)));
            orders.put(orderId, targets);
            lastOrderId = orderId;
            return new OpenedCorrectionOrder(orderId, "SUBMITTED");
        }

        @Override
        public void executeAndClose(UUID orderId, UUID actorId) {
            executeCount++;
            orders.getOrDefault(orderId, Map.of())
                    .forEach((unit, keys) -> handler.onCorrectionConfirmed(orderId, unit, keys));
        }
    }

    /** Leitor por mapa: a "fotografia" do cadastro que o teste controla. */
    static final class MapFieldReader implements CadastralFieldReader {
        private final Map<String, String> values = new LinkedHashMap<>();

        void set(String targetType, UUID targetId, String field, String value) {
            values.put(key(targetType, targetId, field), value);
        }

        @Override
        public String currentValue(String targetType, UUID targetId, String field) {
            return values.get(key(targetType, targetId, field));
        }

        private static String key(String targetType, UUID targetId, String field) {
            return targetType + "|" + targetId + "|" + field;
        }
    }

    /** Aplicador que registra as chamadas e devolve ids de historico. */
    static final class RecordingApplier implements CadastralChangeApplier {
        record Call(UUID serviceOrderId, String targetType, UUID targetId, String field,
                    String oldValue, String newValue, UUID actorId, UUID changeRequestId) {
        }

        final List<Call> calls = new ArrayList<>();

        @Override
        public UUID apply(UUID serviceOrderId, String targetType, UUID targetId, String field,
                          String oldValue, String newValue, UUID actorId) {
            UUID changeRequestId = UUID.randomUUID();
            calls.add(new Call(serviceOrderId, targetType, targetId, field,
                    oldValue, newValue, actorId, changeRequestId));
            return changeRequestId;
        }
    }

    /** Portao de aparelho configuravel (padrao ED-18). */
    static final class Gate implements CollectionDeviceGate {
        boolean refusing;

        @Override
        public void assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now) {
            if (refusing) {
                throw new IllegalStateException("aparelho nao registrado ou bloqueado nao sincroniza");
            }
        }
    }
}
