package br.com.ziipsan.core.certification.governance;

import java.util.*; import java.time.LocalDate;
public interface GovernanceStore {
    void saveCriterion(UUID tenantId, RiskCriterion c); Optional<RiskCriterion> criterion(UUID tenantId);
    void saveRisk(RiskRecord r); List<RiskRecord> risks(UUID tenantId);
    void saveObjective(ObjectiveRecord o); List<ObjectiveRecord> objectives(UUID tenantId);
    void saveSupplier(SupplierRecord s); List<SupplierRecord> suppliers(UUID tenantId);
    void saveEvaluation(SupplierEvaluation e); List<SupplierEvaluation> evaluations(UUID supplierId);
    void saveCompetence(CompetenceRecord c); List<CompetenceRecord> competences(UUID tenantId);
    void saveLegal(LegalRequirement l); void saveConformity(UUID id, LegalRequirement.Conformity c, LocalDate on);
    List<LegalRequirement> legal(UUID tenantId); Map<UUID,LegalRequirement.Conformity> conformities(UUID tenantId);
    void saveProgram(AuditAndReview.AuditProgram p); void saveFinding(AuditAndReview.AuditFinding f);
    /** Leitura do programa por id (correcao D5): addFinding valida existencia e tenant antes de gravar. */
    Optional<AuditAndReview.AuditProgram> program(UUID programId);
    List<AuditAndReview.AuditProgram> programs(UUID tenantId);
    List<AuditAndReview.AuditFinding> findings(UUID tenantId);
    void saveReview(AuditAndReview.ManagementReview r); List<AuditAndReview.ManagementReview> reviews(UUID tenantId);
    void audit(UUID tenantId, String action, String detailJson, UUID actor);
    List<String> auditTrail(UUID tenantId);
}
