package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Store em memoria da ED-68, para testes (o adapter JDBC e fiacao de integracao). */
public final class InMemoryThirdPartyCollectionStore implements ThirdPartyCollectionStore {

    private final Map<UUID, ThirdPartyContract> contracts = new LinkedHashMap<>();
    private final Map<UUID, OperatorContract> operatorByContract = new LinkedHashMap<>();
    private final Map<UUID, DebtAssignment> assignments = new LinkedHashMap<>();
    private final Map<UUID, RemunerationEntry> remunerationByEvent = new LinkedHashMap<>();
    private final Map<UUID, LocalDate> paymentDateByEvent = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();

    @Override public void saveContract(ThirdPartyContract c) { contracts.put(c.id(), c); }
    @Override public Optional<ThirdPartyContract> contract(UUID id) { return Optional.ofNullable(contracts.get(id)); }
    @Override public List<ThirdPartyContract> contracts(UUID tenantId) {
        return contracts.values().stream().filter(c -> c.tenantId().equals(tenantId)).toList();
    }

    @Override public void saveOperatorContract(OperatorContract o) { operatorByContract.put(o.thirdPartyContractId(), o); }
    @Override public Optional<OperatorContract> operatorContractOf(UUID contractId) {
        return Optional.ofNullable(operatorByContract.get(contractId));
    }

    @Override public void saveAssignment(DebtAssignment a) { assignments.put(a.id(), a); }
    @Override public Optional<DebtAssignment> assignment(UUID id) { return Optional.ofNullable(assignments.get(id)); }
    @Override public List<DebtAssignment> assignments(UUID tenantId) {
        return assignments.values().stream().filter(a -> a.tenantId().equals(tenantId)).toList();
    }
    @Override public List<DebtAssignment> assignmentsOfContract(UUID contractId) {
        return assignments.values().stream().filter(a -> a.contractId().equals(contractId)).toList();
    }
    @Override public Optional<DebtAssignment> activeAssignmentOfDebt(UUID tenantId, UUID debtId) {
        return assignments.values().stream()
                .filter(a -> a.tenantId().equals(tenantId) && a.debtId().equals(debtId)
                        && a.status() == DebtAssignment.Status.ACTIVE)
                .findFirst();
    }
    @Override public Optional<DebtAssignment> assignmentOfDebt(UUID tenantId, UUID debtId) {
        // A ultima gravada ganha: uma divida retirada pode voltar a ser
        // cedida mais tarde; a leitura "actual" e a da cessao mais recente.
        DebtAssignment found = null;
        for (DebtAssignment a : assignments.values()) {
            if (a.tenantId().equals(tenantId) && a.debtId().equals(debtId)) found = a;
        }
        return Optional.ofNullable(found);
    }

    @Override public void saveRemuneration(RemunerationEntry r) { remunerationByEvent.put(r.paymentEventId(), r); }
    @Override public Optional<RemunerationEntry> remunerationOfEvent(UUID tenantId, UUID paymentEventId) {
        RemunerationEntry r = remunerationByEvent.get(paymentEventId);
        return r != null && r.tenantId().equals(tenantId) ? Optional.of(r) : Optional.empty();
    }
    @Override public List<RemunerationEntry> remunerations(UUID tenantId) {
        return remunerationByEvent.values().stream().filter(r -> r.tenantId().equals(tenantId)).toList();
    }

    @Override public void recordPaymentDate(UUID tenantId, UUID paymentEventId, LocalDate paidOn) {
        paymentDateByEvent.put(paymentEventId, paidOn);
    }
    @Override public LocalDate paymentDateOf(UUID tenantId, UUID paymentEventId) {
        return paymentDateByEvent.get(paymentEventId);
    }

    @Override public void audit(UUID tenantId, String action, String detail) {
        audit.add(tenantId + "|" + action + "|" + detail);
    }
    @Override public List<String> auditTrail(UUID tenantId) {
        return audit.stream().filter(s -> s.startsWith(tenantId + "|")).toList();
    }
}
