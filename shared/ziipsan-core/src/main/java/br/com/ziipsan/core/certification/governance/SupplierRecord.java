package br.com.ziipsan.core.certification.governance;

import java.util.UUID;
public record SupplierRecord(UUID id, UUID tenantId, String legalName, String documentRef, Type type, String contactRef) {
    public enum Type { MATERIAL, SERVICE, EXTERNAL_LABORATORY, CONTRACTOR }
    public SupplierRecord {
        if (legalName==null||legalName.isBlank()) throw new GovernanceValidationException("SUPPLIER_NAME_REQUIRED","fornecedor exige razao/nome");
        if (type==null) throw new GovernanceValidationException("SUPPLIER_TYPE_REQUIRED","fornecedor exige tipo (material, servico, laboratorio externo, empreiteira)");
    }
}
