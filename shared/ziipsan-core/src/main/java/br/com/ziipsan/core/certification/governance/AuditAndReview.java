package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.List; import java.util.UUID;
/** Programa/achados de auditoria interna e analise critica pela direcao. */
public final class AuditAndReview {
    private AuditAndReview(){}
    public record AuditProgram(UUID id, UUID tenantId, String title, LocalDate plannedFor) {
        public AuditProgram { if(title==null||title.isBlank()||plannedFor==null) throw new GovernanceValidationException("AUDIT_PROGRAM_REQUIRED","programa de auditoria exige titulo e data planejada"); }
    }
    /** Achado com tenantId proprio (correcao D7), alinhado a coluna tenant_id NOT NULL de cert_audit_findings. */
    public record AuditFinding(UUID id, UUID tenantId, UUID programId, String description, Severity severity, LocalDate dueDate, boolean closed) {
        public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
        public AuditFinding {
            if(tenantId==null) throw new GovernanceValidationException("TENANT_REQUIRED","tenant obrigatorio");
            if(programId==null) throw new GovernanceValidationException("AUDIT_PROGRAM_REQUIRED","achado exige programa de auditoria");
            if(description==null||description.isBlank()||dueDate==null) throw new GovernanceValidationException("AUDIT_FINDING_REQUIRED","achado exige descricao e prazo");
        }
    }
    public record ManagementReview(UUID id, UUID tenantId, LocalDate heldOn, List<String> decisions, String minutesRef) {
        public ManagementReview { if(heldOn==null||decisions==null||decisions.isEmpty()) throw new GovernanceValidationException("REVIEW_DECISIONS_REQUIRED","analise critica exige data e decisoes registradas"); decisions=List.copyOf(decisions); }
    }
}
