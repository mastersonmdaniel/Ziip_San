package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.time.LocalDate;
import java.util.UUID;

/**
 * E2 — Registo do contrato de operador (LGPD) associado ao contrato de
 * cobranca (E1). O gate vive no servico: sem um registo destes para o
 * contrato, NENHUMA cessao acontece (ver
 * {@code ThirdPartyCollectionService.assignDebts}).
 *
 * <p>O registo exige a referencia do instrumento e a data de registo —
 * ambos declarados por quem regista; nada e presumido nem datado por
 * defeito pelo codigo.
 */
public record OperatorContract(UUID id, UUID tenantId, UUID thirdPartyContractId,
                               String reference, LocalDate registeredOn) {

    public OperatorContract {
        if (id == null)
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_ID_REQUIRED", "registo de operador exige id");
        if (tenantId == null)
            throw new ThirdPartyCollectionException("TENANT_REQUIRED", "tenant obrigatorio");
        if (thirdPartyContractId == null)
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_LINK_REQUIRED",
                    "registo de operador exige o contrato de cobranca (E1) a que pertence");
        if (reference == null || reference.isBlank())
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_REFERENCE_REQUIRED",
                    "contrato de operador (LGPD) exige a referencia do instrumento registado");
        if (registeredOn == null)
            throw new ThirdPartyCollectionException("OPERATOR_CONTRACT_DATE_REQUIRED",
                    "contrato de operador (LGPD) exige a data de registo declarada");
    }
}
