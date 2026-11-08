package br.com.ziipsan.core.contractor;

import br.com.ziipsan.core.certification.governance.GovernanceValidationException;

import java.util.Set;
import java.util.UUID;

/**
 * Perfil de empreiteira (ED-37; SG-492): EXTENSAO do cadastro minimo de
 * fornecedor da P3.31 ({@code SupplierRecord} de tipo CONTRACTOR, tabela
 * {@code cert_suppliers}). Nao ha cadastro paralelo: este registro so guarda
 * o que a P3.31 nao tem (especialidades, area de atuacao e responsavel
 * tecnico) e referencia o fornecedor pelo id — razao social, documento e
 * contato continuam vivendo SOMENTE no cadastro da P3.31.
 */
public record ContractorProfile(UUID id, UUID tenantId, UUID supplierId,
                                Set<String> specialties, String coverageRef,
                                String technicalResponsibleRef) {
    public ContractorProfile {
        if (id == null) throw new GovernanceValidationException("CONTRACTOR_PROFILE_ID_REQUIRED", "perfil de empreiteira exige id");
        if (tenantId == null) throw new GovernanceValidationException("TENANT_REQUIRED", "tenant obrigatorio");
        if (supplierId == null) throw new GovernanceValidationException("CONTRACTOR_SUPPLIER_REQUIRED", "perfil exige o fornecedor CONTRACTOR da P3.31");
        if (specialties == null || specialties.isEmpty())
            throw new GovernanceValidationException("CONTRACTOR_SPECIALTY_REQUIRED", "empreiteira exige ao menos uma especialidade declarada");
        specialties = Set.copyOf(specialties);
        if (specialties.stream().anyMatch(s -> s == null || s.isBlank()))
            throw new GovernanceValidationException("CONTRACTOR_SPECIALTY_REQUIRED", "especialidade em branco nao e especialidade");
        if (coverageRef == null || coverageRef.isBlank())
            throw new GovernanceValidationException("CONTRACTOR_COVERAGE_REQUIRED", "empreiteira exige area de atuacao declarada");
        if (technicalResponsibleRef == null || technicalResponsibleRef.isBlank())
            throw new GovernanceValidationException("CONTRACTOR_RESPONSIBLE_REQUIRED", "empreiteira exige responsavel tecnico declarado");
    }
}
