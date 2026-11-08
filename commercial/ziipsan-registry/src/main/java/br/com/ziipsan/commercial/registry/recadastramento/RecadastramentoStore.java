package br.com.ziipsan.commercial.registry.recadastramento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia do recadastramento (ED-16): a fiacao de producao liga
 * esta interface as entidades JPA desta entrega
 * ({@code recadastramento_batches}, {@code recadastramento_collected_fields},
 * {@code recadastramento_collection_errors}, {@code recadastramento_history}
 * — SQL em {@code db/pendente/}, sem numero de migration reservado no
 * plano); os testes usam a implementacao em memoria.
 */
public interface RecadastramentoStore {

    void saveBatch(RecadastramentoBatch batch);

    Optional<RecadastramentoBatch> findBatch(UUID batchId);

    Optional<RecadastramentoBatch> findBatchByServiceOrderId(UUID serviceOrderId);

    void saveCollected(CollectedField collected);

    Optional<CollectedField> findCollected(UUID collectedId);

    Optional<CollectedField> findCollectedByTask(UUID batchId, UUID taskId);

    List<CollectedField> findCollectedByBatch(UUID batchId);

    void saveError(CollectionError error);

    List<CollectionError> findErrorsByBatch(UUID batchId);

    void saveHistory(RecadastramentoHistoryEntry entry);

    List<RecadastramentoHistoryEntry> findHistoryByBatch(UUID batchId);
}
