package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * E9 — Remuneracao da empresa terceirizada, calculada PELO EVENTO de
 * pagamento atribuido (E7): {@code percentual do contrato x valor do
 * pagamento}. Aritmetica monetaria declarada: resultado em escala 2
 * (centavos) com {@link RoundingMode#HALF_UP}. O percentual aplicado fica
 * congelado na entrada (o do contrato no momento do evento).
 *
 * <p>Idempotencia: uma entrada por {@code paymentEventId} — a unicidade e
 * travada no servico/store e, no SQL (E12), por constraint unica.
 */
public record RemunerationEntry(UUID id, UUID tenantId, UUID contractId, UUID companySupplierId,
                                UUID assignmentId, UUID paymentEventId, BigDecimal paymentAmount,
                                BigDecimal percentApplied, BigDecimal remunerationAmount) {

    public RemunerationEntry {
        if (id == null || tenantId == null || contractId == null || companySupplierId == null
                || assignmentId == null || paymentEventId == null) {
            throw new ThirdPartyCollectionException("REMUNERATION_LINK_REQUIRED",
                    "remuneracao exige id, tenant, contrato, empresa, cessao e evento de pagamento");
        }
        if (paymentAmount == null || paymentAmount.signum() <= 0)
            throw new ThirdPartyCollectionException("REMUNERATION_PAYMENT_INVALID",
                    "remuneracao exige o valor positivo do pagamento atribuido");
        if (percentApplied == null || percentApplied.signum() <= 0
                || percentApplied.compareTo(new BigDecimal("100")) > 0)
            throw new ThirdPartyCollectionException("REMUNERATION_PERCENT_OUT_OF_RANGE",
                    "percentual aplicado fora da faixa (0, 100]: " + percentApplied);
        if (remunerationAmount == null || remunerationAmount.signum() < 0)
            throw new ThirdPartyCollectionException("REMUNERATION_AMOUNT_INVALID",
                    "valor da remuneracao nao pode ser nulo/negativo");
    }

    /** Calculo unico da remuneracao: percentual do contrato x pagamento, escala 2, HALF_UP. */
    public static BigDecimal compute(BigDecimal percent, BigDecimal paymentAmount) {
        return paymentAmount.multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    public static RemunerationEntry forPayment(UUID id, UUID tenantId, ThirdPartyContract contract,
                                               DebtAssignment assignment, PaymentEvent event) {
        return new RemunerationEntry(id, tenantId, contract.id(), contract.companySupplierId(),
                assignment.id(), event.eventId(), event.amount(), contract.remunerationPercent(),
                compute(contract.remunerationPercent(), event.amount()));
    }
}
