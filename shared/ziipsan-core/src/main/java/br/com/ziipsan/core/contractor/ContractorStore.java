package br.com.ziipsan.core.contractor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia da ED-37 (adapter JDBC na fiacao; memoria nos testes). */
public interface ContractorStore {
    void saveProfile(ContractorProfile p);
    Optional<ContractorProfile> profileBySupplier(UUID tenantId, UUID supplierId);
    List<ContractorProfile> profiles(UUID tenantId);

    void saveContract(ContractorContract c);
    Optional<ContractorContract> contract(UUID contractId);
    List<ContractorContract> contracts(UUID tenantId);

    void saveClause(Penalties.PenaltyClause c);
    Optional<Penalties.PenaltyClause> clause(UUID clauseId);
    List<Penalties.PenaltyClause> clauses(UUID contractId);

    void saveApplication(Penalties.PenaltyApplication a);
    Optional<Penalties.PenaltyApplication> application(UUID applicationId);
    List<Penalties.PenaltyApplication> applications(UUID contractId);
    List<Penalties.PenaltyApplication> applicationsOfMeasurement(UUID measurementId);

    void saveMeasurement(Measurement.ContractorMeasurement m);
    Optional<Measurement.ContractorMeasurement> measurement(UUID measurementId);
    List<Measurement.ContractorMeasurement> measurements(UUID tenantId);
    /** Medicoes vivas (nao REJECTED) do contrato — base da trava anti-dupla-medicao. */
    List<Measurement.ContractorMeasurement> liveMeasurements(UUID contractId);

    void audit(UUID tenantId, String action, String detail, UUID actor);
    List<String> auditTrail(UUID tenantId);
}
