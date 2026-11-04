package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Store em memória (testes do motor, sem banco). Reproduz as regras de
 * unicidade do banco — (sistema, chave) por lote e chave legada por
 * unidade — para os testes de idempotência valerem também sem PostgreSQL.
 */
public final class InMemoryLegacyStagingStore implements LegacyStagingStore {

    private final Map<UUID, LegacyBatch> batches = new LinkedHashMap<>();
    private final Map<UUID, List<StagedUnit>> units = new LinkedHashMap<>();
    private final Map<UUID, List<StagedInstallment>> installments = new LinkedHashMap<>();
    private final Map<UUID, List<LegacyLineageRow>> lineage = new LinkedHashMap<>();
    private final Map<UUID, List<LegacyReconciliation.Row>> reconciliation = new LinkedHashMap<>();

    @Override
    public LegacyBatch createBatch(String sourceSystem, String mappingName, String mappingVersion,
                                   String externalBatchKey, String sourceKind, UUID createdBy) {
        LegacyBatch batch = new LegacyBatch(UUID.randomUUID(), sourceSystem, mappingName, mappingVersion,
                externalBatchKey, sourceKind, LegacyBatch.Status.STAGED, null);
        batches.put(batch.id(), batch);
        units.put(batch.id(), new ArrayList<>());
        installments.put(batch.id(), new ArrayList<>());
        lineage.put(batch.id(), new ArrayList<>());
        reconciliation.put(batch.id(), new ArrayList<>());
        return batch;
    }

    @Override
    public Optional<LegacyBatch> findBatchByKey(String sourceSystem, String externalBatchKey) {
        return batches.values().stream()
                .filter(b -> b.sourceSystem().equals(sourceSystem)
                        && b.externalBatchKey().equals(externalBatchKey))
                .findFirst();
    }

    @Override
    public Optional<LegacyBatch> findBatchById(UUID batchId) {
        return Optional.ofNullable(batches.get(batchId));
    }

    @Override
    public void insertUnits(UUID batchId, List<StagedUnit> newUnits) {
        List<StagedUnit> existing = units.computeIfAbsent(batchId, id -> new ArrayList<>());
        for (StagedUnit unit : newUnits) {
            if (existing.stream().anyMatch(u -> u.legacyKey().equals(unit.legacyKey()))) {
                throw new IllegalStateException("Chave legada duplicada no lote: " + unit.legacyKey());
            }
            existing.add(unit);
        }
    }

    @Override
    public List<StagedUnit> listUnits(UUID batchId) {
        return List.copyOf(units.getOrDefault(batchId, List.of()));
    }

    @Override
    public void insertInstallments(UUID batchId, List<StagedInstallment> newInstallments) {
        installments.computeIfAbsent(batchId, id -> new ArrayList<>()).addAll(newInstallments);
    }

    @Override
    public List<StagedInstallment> listInstallments(UUID batchId) {
        return List.copyOf(installments.getOrDefault(batchId, List.of()));
    }

    @Override
    public void insertLineage(UUID batchId, String recordKind, String sourceSystem, String sourceKey,
                              String mappingName, String mappingVersion, UUID stagingId) {
        lineage.computeIfAbsent(batchId, id -> new ArrayList<>()).add(new LegacyLineageRow(
                UUID.randomUUID(), recordKind, sourceSystem, sourceKey,
                mappingName, mappingVersion, stagingId, null, null, null));
    }

    @Override
    public List<LegacyLineageRow> listLineage(UUID batchId) {
        return List.copyOf(lineage.getOrDefault(batchId, List.of()));
    }

    @Override
    public void updateUnit(StagedUnit unit) {
        // Em memória, a unidade já é a referência guardada: nada a copiar.
    }

    @Override
    public void markUnitApplied(UUID batchId, UUID stagingId, UUID unitId, UUID orderId) {
        List<LegacyLineageRow> rows = lineage.getOrDefault(batchId, List.of());
        for (int i = 0; i < rows.size(); i++) {
            LegacyLineageRow row = rows.get(i);
            if (row.stagingId() != null && row.stagingId().equals(stagingId)) {
                rows.set(i, new LegacyLineageRow(row.id(), row.recordKind(), row.sourceSystem(),
                        row.sourceKey(), row.mappingName(), row.mappingVersion(), row.stagingId(),
                        "consumer_units", unitId, orderId));
            }
        }
    }

    @Override
    public void saveReconciliation(UUID batchId, List<LegacyReconciliation.Row> rows) {
        reconciliation.put(batchId, new ArrayList<>(rows));
    }

    @Override
    public List<LegacyReconciliation.Row> listReconciliation(UUID batchId) {
        return List.copyOf(reconciliation.getOrDefault(batchId, List.of()));
    }

    @Override
    public void updateBatchOrder(UUID batchId, UUID orderId) {
        LegacyBatch batch = batches.get(batchId);
        if (batch != null) {
            batches.put(batchId, new LegacyBatch(batch.id(), batch.sourceSystem(), batch.mappingName(),
                    batch.mappingVersion(), batch.externalBatchKey(), batch.sourceKind(),
                    batch.status(), orderId));
        }
    }

    @Override
    public void markBatchApplied(UUID batchId) {
        LegacyBatch batch = batches.get(batchId);
        if (batch != null) {
            batches.put(batchId, new LegacyBatch(batch.id(), batch.sourceSystem(), batch.mappingName(),
                    batch.mappingVersion(), batch.externalBatchKey(), batch.sourceKind(),
                    LegacyBatch.Status.APPLIED, batch.batchOrderId()));
        }
    }

    @Override
    public void deleteBatch(UUID batchId) {
        batches.remove(batchId);
        units.remove(batchId);
        installments.remove(batchId);
        lineage.remove(batchId);
        reconciliation.remove(batchId);
    }
}
