package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia da ED-68 (adapter JDBC na fiacao; memoria nos testes — precedente ED-37). */
public interface ThirdPartyCollectionStore {

    void saveContract(ThirdPartyContract c);
    Optional<ThirdPartyContract> contract(UUID contractId);
    List<ThirdPartyContract> contracts(UUID tenantId);

    void saveOperatorContract(OperatorContract o);
    Optional<OperatorContract> operatorContractOf(UUID contractId);

    void saveAssignment(DebtAssignment a);
    Optional<DebtAssignment> assignment(UUID assignmentId);
    List<DebtAssignment> assignments(UUID tenantId);
    List<DebtAssignment> assignmentsOfContract(UUID contractId);
    /** Cessao ACTIVA da divida neste tenant, se existir (trava de dupla cessao). */
    Optional<DebtAssignment> activeAssignmentOfDebt(UUID tenantId, UUID debtId);
    /** Qualquer cessao da divida neste tenant (a mais recente gravada ganha nas leituras do servico). */
    Optional<DebtAssignment> assignmentOfDebt(UUID tenantId, UUID debtId);

    void saveRemuneration(RemunerationEntry r);
    Optional<RemunerationEntry> remunerationOfEvent(UUID tenantId, UUID paymentEventId);
    List<RemunerationEntry> remunerations(UUID tenantId);

    /** Data do pagamento por evento (para o relatorio E10 derivar por periodo). */
    void recordPaymentDate(UUID tenantId, UUID paymentEventId, LocalDate paidOn);
    LocalDate paymentDateOf(UUID tenantId, UUID paymentEventId);

    void audit(UUID tenantId, String action, String detail);
    List<String> auditTrail(UUID tenantId);
}
