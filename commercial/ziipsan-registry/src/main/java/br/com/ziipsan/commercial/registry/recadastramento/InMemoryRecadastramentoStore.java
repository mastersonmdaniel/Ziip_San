package br.com.ziipsan.commercial.registry.recadastramento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Store em memoria do recadastramento: para os testes da ED-16 e para
 * exercicios sobre massa sintetica. A fiacao de producao liga a mesma
 * interface as entidades JPA.
 */
public final class InMemoryRecadastramentoStore implements RecadastramentoStore {

    private final Map<UUID, RecadastramentoBatch> batches = new LinkedHashMap<>();
    private final Map<UUID, CollectedField> collected = new LinkedHashMap<>();
    private final List<CollectionError> errors = new ArrayList<>();
    private final List<RecadastramentoHistoryEntry> history = new ArrayList<>();

    @Override
    public void saveBatch(RecadastramentoBatch batch) {
        batches.put(batch.id(), batch);
    }

    @Override
    public Optional<RecadastramentoBatch> findBatch(UUID batchId) {
        return Optional.ofNullable(batches.get(batchId));
    }

    @Override
    public Optional<RecadastramentoBatch> findBatchByServiceOrderId(UUID serviceOrderId) {
        return batches.values().stream()
                .filter(b -> b.serviceOrderId().equals(serviceOrderId))
                .findFirst();
    }

    @Override
    public void saveCollected(CollectedField field) {
        collected.put(field.id(), field);
    }

    @Override
    public Optional<CollectedField> findCollected(UUID collectedId) {
        return Optional.ofNullable(collected.get(collectedId));
    }

    @Override
    public Optional<CollectedField> findCollectedByTask(UUID batchId, UUID taskId) {
        return collected.values().stream()
                .filter(c -> c.batchId().equals(batchId) && c.taskId().equals(taskId))
                .findFirst();
    }

    @Override
    public List<CollectedField> findCollectedByBatch(UUID batchId) {
        List<CollectedField> result = new ArrayList<>();
        for (CollectedField field : collected.values()) {
            if (field.batchId().equals(batchId)) {
                result.add(field);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void saveError(CollectionError error) {
        errors.add(error);
    }

    @Override
    public List<CollectionError> findErrorsByBatch(UUID batchId) {
        List<CollectionError> result = new ArrayList<>();
        for (CollectionError error : errors) {
            if (error.batchId().equals(batchId)) {
                result.add(error);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void saveHistory(RecadastramentoHistoryEntry entry) {
        history.add(entry);
    }

    @Override
    public List<RecadastramentoHistoryEntry> findHistoryByBatch(UUID batchId) {
        List<RecadastramentoHistoryEntry> result = new ArrayList<>();
        for (RecadastramentoHistoryEntry entry : history) {
            if (entry.batchId().equals(batchId)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /** Todos os lotes (leitura de teste). */
    public List<RecadastramentoBatch> allBatches() {
        return List.copyOf(batches.values());
    }
}
