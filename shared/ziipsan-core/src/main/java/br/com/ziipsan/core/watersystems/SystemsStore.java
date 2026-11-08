package br.com.ziipsan.core.watersystems;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia (porta) da ED-79 — a implementacao JDBC/JPA e da integracao; aqui so o contrato. */
public interface SystemsStore {
    // sistemas e componentes
    void saveSystem(WaterSystem system);
    Optional<WaterSystem> system(UUID tenantId, String code);
    List<WaterSystem> systems(UUID tenantId);
    void saveComponent(WaterSystem.SystemComponent component);
    List<WaterSystem.SystemComponent> components(UUID tenantId, String systemCode);

    // PSA
    void savePlan(WaterSafetyPlan.Plan plan);
    Optional<WaterSafetyPlan.Plan> plan(UUID tenantId, UUID planId);
    List<WaterSafetyPlan.Plan> plans(UUID tenantId, String supplySystemCode);
    void saveLink(WaterSafetyPlan.HazardLink link);
    List<WaterSafetyPlan.HazardLink> links(UUID tenantId, UUID planId);

    // relatorio anual
    void saveReport(AnnualQualityReport report);
    Optional<AnnualQualityReport> report(UUID tenantId, UUID reportId);
    Optional<AnnualQualityReport> reportFor(UUID tenantId, UUID consumerUnitId, int year);
    void saveIssuance(AnnualReportIssuanceService.IssuanceRecord issuance);
    List<AnnualReportIssuanceService.IssuanceRecord> issuances(UUID tenantId, UUID reportId);

    // Carta de Servicos
    void saveCharter(ServiceCharter charter);
    Optional<ServiceCharter> charter(UUID tenantId, UUID charterId);
    List<ServiceCharter> charters(UUID tenantId);
}
