package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.UUID;
/** Risco/oportunidade. Categorias cobrem continuidade do prestador, cibernetico e resiliencia climatica do aceite. */
public record RiskRecord(UUID id, UUID tenantId, String title, Kind kind, Category category,
        int probability, int impact, String contingencyPlan, String interruptionRef, UUID occurrenceId, LocalDate reviewDue) {
    public enum Kind { RISK, OPPORTUNITY }
    public enum Category { OPERATIONAL, CYBER, CLIMATE_RESILIENCE, PROVIDER_CONTINUITY, GENERAL_DECLARED }
    /** Canal de comunicacao a agencia exigido pelo aceite: parametro do tenant, nunca valor embutido.
     *  Consumido de verdade por GovernanceService via porta TenantParameters (correcao D6). */
    public static final String AGENCY_CHANNEL_PARAMETER = "reg.incident_report_channel";
    public RiskRecord {
        if (tenantId==null) throw new GovernanceValidationException("TENANT_REQUIRED","tenant obrigatorio");
        if (title==null||title.isBlank()) throw new GovernanceValidationException("RISK_TITLE_REQUIRED","titulo obrigatorio");
        if (kind==null||category==null) throw new GovernanceValidationException("RISK_KIND_CATEGORY_REQUIRED","tipo e categoria obrigatorios");
        if ((category==Category.OPERATIONAL||category==Category.CYBER||category==Category.PROVIDER_CONTINUITY)
            && (contingencyPlan==null||contingencyPlan.isBlank()))
            throw new GovernanceValidationException("CONTINGENCY_PLAN_REQUIRED","risco de continuidade/operacional/cibernetico exige plano de contingencia (modo degradado)");
        if (reviewDue==null) throw new GovernanceValidationException("RISK_REVIEW_DUE_REQUIRED","revisao do risco exige vencimento declarado");
    }
    /** Vinculo declarado com a ocorrencia da P2.25 (cert_occurrence): referencia opcional;
     *  a existencia e travada no banco pela FK (tenant_id, occurrence_id) da V21_3. */
    public boolean linkedToOccurrence(){ return occurrenceId!=null; }
}
