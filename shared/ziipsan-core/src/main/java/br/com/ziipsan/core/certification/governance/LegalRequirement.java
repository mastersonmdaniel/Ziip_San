package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.UUID;
/** Requisito legal por tenant. A conformidade vive NESTE record (correcao D7), junto da data
 *  da avaliacao — antes vivia num mapa lateral da store e a data era descartada. */
public record LegalRequirement(UUID id, UUID tenantId, String title, String sourceRef, Origin origin, LocalDate evaluationDue,
        Conformity conformity, LocalDate conformityEvaluatedOn) {
    public enum Origin { TENANT_DECLARED, SANITATION_FACTORY_CATALOG }
    public enum Conformity { UNDER_EVALUATION, CONFORMING, NON_CONFORMING, NOT_APPLICABLE_DECLARED }
    public LegalRequirement {
        if (title==null||title.isBlank()) throw new GovernanceValidationException("LEGAL_TITLE_REQUIRED","requisito legal exige titulo");
        if (sourceRef==null||sourceRef.isBlank()) throw new GovernanceValidationException("LEGAL_SOURCE_REQUIRED","requisito legal exige fonte declarada (nenhuma norma e inventada)");
        if (evaluationDue==null) throw new GovernanceValidationException("LEGAL_DUE_REQUIRED","requisito legal exige vencimento de avaliacao");
        if (conformity==null) conformity = Conformity.UNDER_EVALUATION;
    }
    /** Construtor de registro inicial: conformidade em avaliacao, sem data de avaliacao. */
    public LegalRequirement(UUID id, UUID tenantId, String title, String sourceRef, Origin origin, LocalDate evaluationDue) {
        this(id, tenantId, title, sourceRef, origin, evaluationDue, Conformity.UNDER_EVALUATION, null);
    }
    /** Nova versao do requisito com a conformidade avaliada em {@code on} (record e imutavel). */
    public LegalRequirement withConformity(Conformity c, LocalDate on) {
        return new LegalRequirement(id, tenantId, title, sourceRef, origin, evaluationDue, c, on);
    }
}
