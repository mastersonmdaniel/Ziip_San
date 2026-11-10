package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Limites de retificacao (aceite da P2.20): o teto de
 * {@code bill.rectification_limits_against_user} so alcanca retificacao
 * <b>contra</b> o usuario -- a que aumenta o total a pagar. Correcao a
 * favor do usuario (total menor) <b>nunca</b> e limitada, e retificacao
 * neutra (mesmo total) nao consome teto. Regra pura.
 */
public final class RectificationLimitRule {

    /** Direcao da retificacao, do ponto de vista de quem paga. */
    public enum Direction {
        AGAINST_USER,
        IN_FAVOR_OF_USER,
        NEUTRAL
    }

    private RectificationLimitRule() {
    }

    public static Direction direction(BigDecimal previousTotal, BigDecimal newTotal) {
        Objects.requireNonNull(previousTotal, "previousTotal");
        Objects.requireNonNull(newTotal, "newTotal");
        int cmp = newTotal.compareTo(previousTotal);
        if (cmp > 0) {
            return Direction.AGAINST_USER;
        }
        if (cmp < 0) {
            return Direction.IN_FAVOR_OF_USER;
        }
        return Direction.NEUTRAL;
    }

    /**
     * @param againstUserAlreadyInYear retificacoes contra o usuario ja feitas
     *                                 nesta unidade no ano civil da operacao
     * @throws IllegalArgumentException so quando a direcao e contra o usuario
     *                                  e o teto configurado foi atingido
     */
    public static void check(MaintenanceParameters parameters, BigDecimal previousTotal, BigDecimal newTotal,
                             int againstUserAlreadyInYear) {
        if (againstUserAlreadyInYear < 0) {
            throw new IllegalArgumentException("Contagem de retificacoes nao pode ser negativa");
        }
        // Le a chave sempre: chave ausente e erro explicito mesmo para
        // retificacao a favor (o tenant precisa ter a politica declarada).
        Integer max = parameters.rectificationMaxPerUnitAndYear();
        if (direction(previousTotal, newTotal) != Direction.AGAINST_USER) {
            return;
        }
        if (max == null) {
            return;
        }
        if (againstUserAlreadyInYear >= max) {
            throw new IllegalArgumentException("bill.rectification_limits_against_user: unidade ja teve "
                    + againstUserAlreadyInYear + " retificacao(oes) contra o usuario no ano; teto e " + max
                    + " (correcao a favor do usuario nunca e limitada)");
        }
    }

    /** Diferenca apurada da retificacao: sem multa nem juros, por definicao (aceite). */
    public static BigDecimal differenceWithoutCharges(BigDecimal previousTotal, BigDecimal newTotal) {
        Objects.requireNonNull(previousTotal, "previousTotal");
        Objects.requireNonNull(newTotal, "newTotal");
        return newTotal.subtract(previousTotal).setScale(Money.SCALE, java.math.RoundingMode.UNNECESSARY);
    }
}
