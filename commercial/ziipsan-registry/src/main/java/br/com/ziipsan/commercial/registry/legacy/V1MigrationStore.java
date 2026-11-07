package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P2.10: porta de persistência das tabelas próprias da V14_2 (staging e
 * aplicado do histórico, acordos migrados, vínculo prestação→débito e
 * situações de unidade), no mesmo desenho de portas do framework da P1.2:
 * o motor ({@link V1ImportService}) fala com esta interface, os testes
 * rodam contra {@link InMemoryV1MigrationStore} e a integração liga a
 * implementação JDBC sobre as tabelas da V14_2.
 *
 * <p>As unidades e as prestações NÃO passam por aqui: vivem no staging da
 * P1.2 ({@link LegacyStagingStore}), que a P2.10 apenas lê e cuja virada
 * de status das prestações ela comanda pelo gateway de débitos.
 */
public interface V1MigrationStore {

    // Histórico encenado ------------------------------------------------

    void insertHistory(UUID batchId, List<V1HistoryRecord> records);

    List<V1HistoryRecord> listHistory(UUID batchId);

    void updateHistory(V1HistoryRecord record);

    /** Chaves (unidade, mês) já aplicadas em qualquer lote — a trava de idempotência do histórico. */
    boolean historyAlreadyApplied(String unitLegacyKey, java.time.LocalDate referenceMonth);

    void markHistoryApplied(V1HistoryRecord record, UUID unitId, UUID actorId);

    // Acordos migrados ---------------------------------------------------

    /** Identidade estável do acordo no lote: cria na primeira vez, devolve a mesma depois. */
    UUID agreementId(UUID batchId, String legacyAgreementRef);

    // Vínculo prestação → débito ----------------------------------------

    Optional<UUID> debitForInstallment(UUID stagingInstallmentId);

    void linkInstallmentDebit(UUID batchId, UUID stagingInstallmentId, UUID debitReceivableId);

    /** Débito de resíduo de saldo já criado para a unidade encenada, se houver. */
    Optional<UUID> residualDebitForUnit(UUID stagingUnitId);

    void linkResidualDebit(UUID batchId, UUID stagingUnitId, UUID debitReceivableId);

    // Situações de unidade (CAD-68) --------------------------------------

    boolean situationRecorded(UUID batchId, UUID stagingUnitId);

    void recordSituation(UUID batchId, UUID stagingUnitId, UUID unitId, String occupancyStatus);
}
