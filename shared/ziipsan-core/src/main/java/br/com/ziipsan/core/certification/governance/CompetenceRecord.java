package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.Set; import java.util.UUID;
/** Treinamento/competencia. Alimenta a verificacao de habilitacao dos tipos de OS da P1.31 (OPE-14) via GovernanceService#isQualified. */
public record CompetenceRecord(UUID id, UUID tenantId, UUID personId, String competenceCode, Set<String> serviceOrderTypes, LocalDate validUntil, String evidenceRef) {
    public CompetenceRecord {
        if (personId==null) throw new GovernanceValidationException("COMPETENCE_PERSON_REQUIRED","competencia exige pessoa");
        if (competenceCode==null||competenceCode.isBlank()) throw new GovernanceValidationException("COMPETENCE_CODE_REQUIRED","competencia exige codigo");
        if (serviceOrderTypes==null||serviceOrderTypes.isEmpty()) throw new GovernanceValidationException("COMPETENCE_OS_TYPES_REQUIRED","competencia exige ao menos um tipo de OS habilitado");
        if (validUntil==null) throw new GovernanceValidationException("COMPETENCE_VALIDITY_REQUIRED","competencia exige validade");
        serviceOrderTypes = Set.copyOf(serviceOrderTypes);
    }
    public boolean qualifies(String osType, LocalDate on){ return serviceOrderTypes.contains(osType) && !validUntil.isBefore(on); }
}
