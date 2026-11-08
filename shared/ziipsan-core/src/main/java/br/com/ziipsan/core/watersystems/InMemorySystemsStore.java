package br.com.ziipsan.core.watersystems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Store em memoria para o nucleo/testes — a persistencia real e da integracao (SQL em db/pendente). */
public final class InMemorySystemsStore implements SystemsStore {
    private final Map<String, WaterSystem> systems = new LinkedHashMap<>();
    private final List<WaterSystem.SystemComponent> components = new ArrayList<>();
    private final Map<UUID, WaterSafetyPlan.Plan> plans = new LinkedHashMap<>();
    private final List<WaterSafetyPlan.HazardLink> links = new ArrayList<>();
    private final Map<UUID, AnnualQualityReport> reports = new LinkedHashMap<>();
    private final List<AnnualReportIssuanceService.IssuanceRecord> issuances = new ArrayList<>();
    private final Map<UUID, ServiceCharter> charters = new LinkedHashMap<>();

    private static String key(UUID tenantId, String code) { return tenantId + "|" + code; }

    @Override public void saveSystem(WaterSystem s) { systems.put(key(s.tenantId(), s.code()), s); }
    @Override public Optional<WaterSystem> system(UUID t, String code) { return Optional.ofNullable(systems.get(key(t, code))); }
    @Override public List<WaterSystem> systems(UUID t) {
        return systems.values().stream().filter(s -> s.tenantId().equals(t)).toList();
    }
    @Override public void saveComponent(WaterSystem.SystemComponent c) {
        components.removeIf(x -> x.id().equals(c.id()));
        components.add(c);
    }
    @Override public List<WaterSystem.SystemComponent> components(UUID t, String systemCode) {
        return components.stream().filter(c -> c.tenantId().equals(t) && c.systemCode().equals(systemCode)).toList();
    }

    @Override public void savePlan(WaterSafetyPlan.Plan p) { plans.put(p.id(), p); }
    @Override public Optional<WaterSafetyPlan.Plan> plan(UUID t, UUID planId) {
        WaterSafetyPlan.Plan p = plans.get(planId);
        return p != null && p.tenantId().equals(t) ? Optional.of(p) : Optional.empty();
    }
    @Override public List<WaterSafetyPlan.Plan> plans(UUID t, String supplySystemCode) {
        return plans.values().stream()
                .filter(p -> p.tenantId().equals(t) && p.supplySystemCode().equals(supplySystemCode)).toList();
    }
    @Override public void saveLink(WaterSafetyPlan.HazardLink l) { links.add(l); }
    @Override public List<WaterSafetyPlan.HazardLink> links(UUID t, UUID planId) {
        return links.stream().filter(l -> l.tenantId().equals(t) && l.planId().equals(planId)).toList();
    }

    @Override public void saveReport(AnnualQualityReport r) { reports.put(r.id(), r); }
    @Override public Optional<AnnualQualityReport> report(UUID t, UUID reportId) {
        AnnualQualityReport r = reports.get(reportId);
        return r != null && r.tenantId().equals(t) ? Optional.of(r) : Optional.empty();
    }
    @Override public Optional<AnnualQualityReport> reportFor(UUID t, UUID consumerUnitId, int year) {
        return reports.values().stream()
                .filter(r -> r.tenantId().equals(t) && r.consumerUnitId().equals(consumerUnitId) && r.year() == year)
                .findFirst();
    }
    @Override public void saveIssuance(AnnualReportIssuanceService.IssuanceRecord i) { issuances.add(i); }
    @Override public List<AnnualReportIssuanceService.IssuanceRecord> issuances(UUID t, UUID reportId) {
        return issuances.stream().filter(i -> i.tenantId().equals(t) && i.reportId().equals(reportId)).toList();
    }

    @Override public void saveCharter(ServiceCharter c) { charters.put(c.id(), c); }
    @Override public Optional<ServiceCharter> charter(UUID t, UUID charterId) {
        ServiceCharter c = charters.get(charterId);
        return c != null && c.tenantId().equals(t) ? Optional.of(c) : Optional.empty();
    }
    @Override public List<ServiceCharter> charters(UUID t) {
        return charters.values().stream().filter(c -> c.tenantId().equals(t)).toList();
    }
}
