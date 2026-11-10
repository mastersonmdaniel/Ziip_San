package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de pagamento (superficie da P3.1, entregue ao servico da ED-68):
 * e DELE que nascem a atribuicao (E7) e a remuneracao (E9). O
 * {@code eventId} e a chave de idempotencia: o mesmo evento processado
 * duas vezes gera UMA so remuneracao.
 */
public record PaymentEvent(UUID eventId, UUID tenantId, UUID debtId,
                           BigDecimal amount, LocalDate paidOn) {

    public PaymentEvent {
        if (eventId == null)
            throw new ThirdPartyCollectionException("PAYMENT_EVENT_ID_REQUIRED",
                    "evento de pagamento exige id (chave de idempotencia da remuneracao)");
        if (tenantId == null)
            throw new ThirdPartyCollectionException("TENANT_REQUIRED", "tenant obrigatorio");
        if (debtId == null)
            throw new ThirdPartyCollectionException("PAYMENT_EVENT_DEBT_REQUIRED",
                    "evento de pagamento exige a divida a que se refere");
        if (amount == null || amount.signum() <= 0)
            throw new ThirdPartyCollectionException("PAYMENT_EVENT_AMOUNT_INVALID",
                    "evento de pagamento exige valor positivo: " + amount);
        if (paidOn == null)
            throw new ThirdPartyCollectionException("PAYMENT_EVENT_DATE_REQUIRED",
                    "evento de pagamento exige a data do pagamento (e ela que se confere contra a janela)");
    }
}
