package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do staging (P1.2). O motor só conhece esta porta: a
 * implementação JDBC escreve nas tabelas da V14_1, e os testes usam a
 * implementação em memória — a lógica de carga, ensaio, conciliação e
 * aplicação é a mesma nos dois casos.
 *
 * <p>Toda implementação é escopada por tenant (o construtor a amarra); os
 * métodos nunca recebem tenant como parâmetro para não haver como trocar
 * de tenant no meio de uma carga.
 */
public interface LegacyStagingStore {

    LegacyBatch createBatch(String sourceSystem, String mappingName, String mappingVersion,
                            String externalBatchKey, String sourceKind, UUID createdBy);

    Optional<LegacyBatch> findBatchByKey(String sourceSystem, String externalBatchKey);

    Optional<LegacyBatch> findBatchById(UUID batchId);

    void insertUnits(UUID batchId, List<StagedUnit> units);

    List<StagedUnit> listUnits(UUID batchId);

    void insertInstallments(UUID batchId, List<StagedInstallment> installments);

    List<StagedInstallment> listInstallments(UUID batchId);

    void insertLineage(UUID batchId, String recordKind, String sourceSystem, String sourceKey,
                       String mappingName, String mappingVersion, UUID stagingId);

    List<LegacyLineageRow> listLineage(UUID batchId);

    /** Grava o estado final de uma unidade encenada (issues, status, unidade aplicada). */
    void updateUnit(StagedUnit unit);

    /** Liga staging → unidade → OS na linhagem (update idempotente por staging). */
    void markUnitApplied(UUID batchId, UUID stagingId, UUID unitId, UUID orderId);

    void saveReconciliation(UUID batchId, List<LegacyReconciliation.Row> rows);

    List<LegacyReconciliation.Row> listReconciliation(UUID batchId);

    void updateBatchOrder(UUID batchId, UUID orderId);

    void markBatchApplied(UUID batchId);

    /** Remove lote e tudo o que ele encenou (rollback de carga que falhou no meio). */
    void deleteBatch(UUID batchId);

    /** Linhagem lida de volta (relatório por registro). */
    record LegacyLineageRow(
            UUID id, String recordKind, String sourceSystem, String sourceKey,
            String mappingName, String mappingVersion, UUID stagingId,
            String targetTable, UUID targetId, UUID serviceOrderId) {
    }
}
