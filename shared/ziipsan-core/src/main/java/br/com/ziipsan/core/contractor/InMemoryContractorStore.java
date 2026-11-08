package br.com.ziipsan.core.contractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryContractorStore implements ContractorStore {
    private final List<ContractorProfile> profiles = new ArrayList<>();
    private final Map<UUID, ContractorContract> contracts = new HashMap<>();
    private final Map<UUID, Penalties.PenaltyClause> clauses = new HashMap<>();
    private final Map<UUID, Penalties.PenaltyApplication> applications = new HashMap<>();
    private final Map<UUID, Measurement.ContractorMeasurement> measurements = new HashMap<>();
    private final Map<UUID, List<String>> trail = new HashMap<>();

    public void saveProfile(ContractorProfile p) { profiles.removeIf(x -> x.id().equals(p.id())); profiles.add(p); }
    public Optional<ContractorProfile> profileBySupplier(UUID t, UUID s) {
        return profiles.stream().filter(x -> x.tenantId().equals(t) && x.supplierId().equals(s)).findFirst();
    }
    public List<ContractorProfile> profiles(UUID t) { return profiles.stream().filter(x -> x.tenantId().equals(t)).toList(); }

    public void saveContract(ContractorContract c) { contracts.put(c.id(), c); }
    public Optional<ContractorContract> contract(UUID id) { return Optional.ofNullable(contracts.get(id)); }
    public List<ContractorContract> contracts(UUID t) { return contracts.values().stream().filter(c -> c.tenantId().equals(t)).toList(); }

    public void saveClause(Penalties.PenaltyClause c) { clauses.put(c.id(), c); }
    public Optional<Penalties.PenaltyClause> clause(UUID id) { return Optional.ofNullable(clauses.get(id)); }
    public List<Penalties.PenaltyClause> clauses(UUID contractId) { return clauses.values().stream().filter(c -> c.contractId().equals(contractId)).toList(); }

    public void saveApplication(Penalties.PenaltyApplication a) { applications.put(a.id(), a); }
    public Optional<Penalties.PenaltyApplication> application(UUID id) { return Optional.ofNullable(applications.get(id)); }
    public List<Penalties.PenaltyApplication> applications(UUID contractId) { return applications.values().stream().filter(a -> a.contractId().equals(contractId)).toList(); }
    public List<Penalties.PenaltyApplication> applicationsOfMeasurement(UUID m) { return applications.values().stream().filter(a -> m.equals(a.measurementIdOrNull())).toList(); }

    public void saveMeasurement(Measurement.ContractorMeasurement m) { measurements.put(m.id(), m); }
    public Optional<Measurement.ContractorMeasurement> measurement(UUID id) { return Optional.ofNullable(measurements.get(id)); }
    public List<Measurement.ContractorMeasurement> measurements(UUID t) { return measurements.values().stream().filter(m -> m.tenantId().equals(t)).toList(); }
    public List<Measurement.ContractorMeasurement> liveMeasurements(UUID contractId) {
        return measurements.values().stream()
                .filter(m -> m.contractId().equals(contractId) && m.status() != Measurement.Status.REJECTED).toList();
    }

    public void audit(UUID t, String a, String d, UUID actor) { trail.computeIfAbsent(t, k -> new ArrayList<>()).add(a + ":" + d); }
    public List<String> auditTrail(UUID t) { return List.copyOf(trail.getOrDefault(t, List.of())); }
}
