package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E1 — Contrato com a empresa de cobranca terceirizada (ED-68).
 *
 * <p>A empresa e referenciada pelo fornecedor CONTRACTOR da ED-37
 * ({@code companySupplierId}) — sem cadastro paralelo de empresa nesta
 * tarefa (precedente ED-37). A JANELA de vigencia (inicio/fim) e o
 * PERCENTUAL de remuneracao sao dados DESTE contrato (ADR-0038): nenhum
 * valor e constante de codigo nem tem padrao de fabrica; a ausencia ou um
 * valor fora da faixa (percentual &gt; 0 e &le; 100; janela nao invertida)
 * e recusa explicita na construcao.
 */
public record ThirdPartyContract(UUID id, UUID tenantId, UUID companySupplierId, String contractNumber,
                                 LocalDate windowStart, LocalDate windowEnd,
                                 BigDecimal remunerationPercent, Status status) {

    public enum Status { ACTIVE, CLOSED }

    public ThirdPartyContract {
        if (id == null)
            throw new ThirdPartyCollectionException("CONTRACT_ID_REQUIRED", "contrato exige id");
        if (tenantId == null)
            throw new ThirdPartyCollectionException("TENANT_REQUIRED", "tenant obrigatorio");
        if (companySupplierId == null)
            throw new ThirdPartyCollectionException("CONTRACT_COMPANY_REQUIRED",
                    "contrato exige a empresa (fornecedor CONTRACTOR da ED-37)");
        if (contractNumber == null || contractNumber.isBlank())
            throw new ThirdPartyCollectionException("CONTRACT_NUMBER_REQUIRED", "contrato exige numero declarado");
        if (windowStart == null || windowEnd == null)
            throw new ThirdPartyCollectionException("CONTRACT_WINDOW_REQUIRED",
                    "contrato exige a janela de vigencia (inicio e fim) declarada no contrato");
        if (windowEnd.isBefore(windowStart))
            throw new ThirdPartyCollectionException("CONTRACT_WINDOW_INVERTED",
                    "fim da janela anterior ao inicio: janela e dado do contrato, nao se presume correcao");
        if (remunerationPercent == null)
            throw new ThirdPartyCollectionException("REMUNERATION_PERCENT_REQUIRED",
                    "percentual de remuneracao e dado do contrato (ADR-0038): ausencia e recusa, nunca um padrao");
        if (remunerationPercent.signum() <= 0 || remunerationPercent.compareTo(new BigDecimal("100")) > 0)
            throw new ThirdPartyCollectionException("REMUNERATION_PERCENT_OUT_OF_RANGE",
                    "percentual de remuneracao fora da faixa (0, 100]: " + remunerationPercent);
        if (status == null)
            throw new ThirdPartyCollectionException("CONTRACT_STATUS_REQUIRED", "contrato exige estado");
    }

    /** A janela do contrato cobre a data (limites incluidos). */
    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(windowStart) && !date.isAfter(windowEnd);
    }

    public ThirdPartyContract withStatus(Status newStatus) {
        return new ThirdPartyContract(id, tenantId, companySupplierId, contractNumber,
                windowStart, windowEnd, remunerationPercent, newStatus);
    }
}
