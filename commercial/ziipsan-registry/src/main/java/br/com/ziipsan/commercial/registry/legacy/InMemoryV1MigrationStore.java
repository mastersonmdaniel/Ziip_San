package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P2.10: {@link V1MigrationStore} em memória, para os testes do motor
 * rodarem sem banco — o mesmo papel do {@code InMemoryLegacyStagingStore}
 * da P1.2. Não é implementação de produção.
 */
public final class InMemoryV1MigrationStore implements V1MigrationStore {

    private final List<V1HistoryRecord> history = new ArrayList<>();
    private final Map<String, UUID> appliedHistory = new LinkedHashMap<>();
    private final Map<String, UUID> agreements = new LinkedHashMap<>();
    private final Map<UUID, UUID> installmentDebits = new LinkedHashMap<>();
    private final Map<String, String> situations = new LinkedHashMap<>();

    @Override
    public void insertHistory(UUID batchId, List<V1HistoryRecord> records) {
        history.addAll(records);
    }

    @Override
    public List<V1HistoryRecord> listHistory(UUID batchId) {
        return history.stream().filter(r -> r.batchId().equals(batchId)).toList();
    }

    @Override
    public void updateHistory(V1HistoryRecord record) {
        // Em memória o registro é o próprio objeto mutável; nada a fazer.
    }

    @Override
    public boolean historyAlreadyApplied(String unitLegacyKey, LocalDate referenceMonth) {
        return appliedHistory.containsKey(unitLegacyKey + "|" + referenceMonth);
    }

    @Override
    public void markHistoryApplied(V1HistoryRecord record, UUID unitId, UUID actorId) {
        record.markApplied();
        appliedHistory.put(record.unitLegacyKey() + "|" + record.referenceMonth(), record.id());
    }

    @Override
    public UUID agreementId(UUID batchId, String legacyAgreementRef) {
        return agreements.computeIfAbsent(batchId + "|" + legacyAgreementRef, k -> UUID.randomUUID());
    }

    @Override
    public Optional<UUID> debitForInstallment(UUID stagingInstallmentId) {
        return Optional.ofNullable(installmentDebits.get(stagingInstallmentId));
    }

    @Override
    public void linkInstallmentDebit(UUID batchId, UUID stagingInstallmentId, UUID debitReceivableId) {
        installmentDebits.putIfAbsent(stagingInstallmentId, debitReceivableId);
    }

    private final Map<UUID, UUID> residualDebits = new LinkedHashMap<>();

    @Override
    public Optional<UUID> residualDebitForUnit(UUID stagingUnitId) {
        return Optional.ofNullable(residualDebits.get(stagingUnitId));
    }

    @Override
    public void linkResidualDebit(UUID batchId, UUID stagingUnitId, UUID debitReceivableId) {
        residualDebits.putIfAbsent(stagingUnitId, debitReceivableId);
    }

    @Override
    public boolean situationRecorded(UUID batchId, UUID stagingUnitId) {
        return situations.containsKey(batchId + "|" + stagingUnitId);
    }

    @Override
    public void recordSituation(UUID batchId, UUID stagingUnitId, UUID unitId, String occupancyStatus) {
        situations.putIfAbsent(batchId + "|" + stagingUnitId, occupancyStatus);
    }

    /** Situações gravadas (inspeção dos testes). */
    public Map<String, String> situations() {
        return Map.copyOf(situations);
    }

    /** Vínculos prestação→débito gravados (inspeção dos testes). */
    public Map<UUID, UUID> installmentDebits() {
        return Map.copyOf(installmentDebits);
    }
}
