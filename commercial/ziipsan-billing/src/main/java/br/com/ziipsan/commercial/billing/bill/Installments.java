package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Parcelas de débito a cobrar e de crédito a realizar.
 *
 * <p>Regra única para os dois: parcela = {@code total / n}, <b>truncada</b> a 2 casas
 * ({@link java.math.RoundingMode#DOWN}); na última parcela entra o que sobrou,
 * {@code total − parcela × (n − 1)}. Assim a soma das {@code n} parcelas é exatamente o total
 * (100,00 em 3 vezes: 33,33 + 33,33 + 33,34; 35,00 em 6: 5,83 × 5 + 5,85).
 */
public final class Installments {

    private Installments() {
    }

    /**
     * Valor da próxima parcela.
     *
     * @param doneBefore parcelas já cobradas (a que se calcula é a de número {@code doneBefore + 1})
     */
    public static BigDecimal next(BigDecimal total, int installments, int doneBefore) {
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Total deve ser positivo: " + total);
        }
        if (installments < 1) {
            throw new IllegalArgumentException("Número de parcelas deve ser pelo menos 1: " + installments);
        }
        if (doneBefore < 0 || doneBefore >= installments) {
            throw new IllegalArgumentException("Parcelas já cobradas fora de [0, " + (installments - 1) + "]: " + doneBefore);
        }
        BigDecimal regular = Money.truncate(total.divide(BigDecimal.valueOf(installments), Money.SCALE, RoundingMode.DOWN));
        if (regular.signum() == 0) {
            throw new IllegalArgumentException("O total " + total + " em " + installments
                    + " parcelas deixa parcelas de R$ 0,00; reduza o número de parcelas");
        }
        if (doneBefore == installments - 1) {
            return Money.roundHalfUp(total.subtract(regular.multiply(BigDecimal.valueOf(installments - 1L))));
        }
        return regular;
    }
}
