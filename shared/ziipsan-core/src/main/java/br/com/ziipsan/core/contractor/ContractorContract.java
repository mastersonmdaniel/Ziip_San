package br.com.ziipsan.core.contractor;

import br.com.ziipsan.core.certification.governance.GovernanceValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contrato com empreiteira (ED-37; SG-493). O contrato pertence a UMA
 * empreiteira (fornecedor CONTRACTOR da P3.31) e tem numero, objeto,
 * vigencia e valor global declarados.
 *
 * <p>FORA DESTA ENTREGA, por desenho do acceptance: campos de integracao
 * orcamentaria (numero de empenho, ordem de compra externa, numero no PNCP)
 * sao da ED-62 e NAO existem neste record; vinculo com obras e projetos e da
 * ED-84 e tambem nao existe aqui.
 */
public record ContractorContract(UUID id, UUID tenantId, UUID contractorSupplierId, String contractNumber,
                                 String objectDescription, LocalDate signedOn,
                                 LocalDate validFrom, LocalDate validTo, BigDecimal totalValue,
                                 Status status) {
    public enum Status { DRAFT, ACTIVE, SUSPENDED, CLOSED }

    public ContractorContract {
        if (id == null) throw new GovernanceValidationException("CONTRACT_ID_REQUIRED", "contrato exige id");
        if (tenantId == null) throw new GovernanceValidationException("TENANT_REQUIRED", "tenant obrigatorio");
        if (contractorSupplierId == null) throw new GovernanceValidationException("CONTRACT_SUPPLIER_REQUIRED", "contrato exige a empreiteira (fornecedor da P3.31)");
        if (contractNumber == null || contractNumber.isBlank())
            throw new GovernanceValidationException("CONTRACT_NUMBER_REQUIRED", "contrato exige numero declarado");
        if (objectDescription == null || objectDescription.isBlank())
            throw new GovernanceValidationException("CONTRACT_OBJECT_REQUIRED", "contrato exige objeto declarado");
        if (validFrom == null || validTo == null)
            throw new GovernanceValidationException("CONTRACT_VALIDITY_REQUIRED", "contrato exige vigencia (inicio e fim)");
        if (validTo.isBefore(validFrom))
            throw new GovernanceValidationException("CONTRACT_VALIDITY_INVERTED", "fim da vigencia anterior ao inicio");
        if (signedOn != null && signedOn.isAfter(validFrom))
            throw new GovernanceValidationException("CONTRACT_SIGNED_AFTER_START", "assinatura posterior ao inicio da vigencia");
        if (totalValue == null || totalValue.signum() < 0)
            throw new GovernanceValidationException("CONTRACT_VALUE_INVALID", "valor global do contrato nao pode ser nulo/negativo");
        if (status == null) throw new GovernanceValidationException("CONTRACT_STATUS_REQUIRED", "contrato exige estado");
    }

    /** Vigencia cobre a data (execucao de OS / periodo de medicao). */
    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(validFrom) && !date.isAfter(validTo);
    }

    public ContractorContract withStatus(Status newStatus) {
        return new ContractorContract(id, tenantId, contractorSupplierId, contractNumber, objectDescription,
                signedOn, validFrom, validTo, totalValue, newStatus);
    }
}
