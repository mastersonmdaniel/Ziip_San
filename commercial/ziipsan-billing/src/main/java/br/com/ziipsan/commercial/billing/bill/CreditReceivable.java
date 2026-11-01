package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Crédito a realizar: valor a favor do cliente abatido da conta em parcelas.
 *
 * @param residual parte já "concedida" em meses anteriores que a conta não comportou e que passa ao
 *                 mês seguinte (um crédito nunca excede a conta)
 */
public record CreditReceivable(UUID id, String origin, BigDecimal total, int installments, int installmentsDone,
                               BigDecimal residual) {

    public CreditReceivable {
        if (id == null || origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("Crédito sem identificação ou origem");
        }
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Valor do crédito deve ser positivo: " + total);
        }
        if (installments < 1 || installmentsDone < 0 || installmentsDone > installments) {
            throw new IllegalArgumentException("Parcelas inconsistentes: " + installmentsDone + "/" + installments);
        }
        if (residual == null || residual.signum() < 0) {
            throw new IllegalArgumentException("Resíduo do crédito deve ser informado e não negativo: " + residual);
        }
    }
}
