package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Aritmetica da projecao (ED-100), unica para todo o pacote:
 * taxas (recuperacao, perda, fracoes de lag) com escala 6 e
 * {@link RoundingMode#HALF_UP}; dinheiro com escala 2 e HALF_UP.
 * Nenhum outro arredondamento existe no pacote — e o que torna cada
 * projecao recalculavel a mao a partir das entradas.
 */
public final class ForecastMath {

    public static final int RATE_SCALE = 6;
    public static final int MONEY_SCALE = 2;

    private ForecastMath() {
    }

    /** Divisao de taxa: numerador/denominador com escala 6, HALF_UP. */
    public static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Normaliza uma taxa para a escala 6. */
    public static BigDecimal rateValue(BigDecimal value) {
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Arredonda dinheiro para centavos, HALF_UP. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Limita uma taxa ao intervalo [0, 1]. */
    public static BigDecimal clampRate(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(RATE_SCALE);
        }
        return rateValue(value);
    }

    /**
     * Escala maxima de um decimal (correcao pos-revisao): dinheiro tem no
     * maximo 2 casas; percentuais de cenario, 4 (coluna NUMERIC(9,4) do
     * SQL pendente); deslocamento de recuperacao, 6 (NUMERIC(7,6)).
     * Decimal com mais casas e recusado nomeado em vez de ser arredondado
     * em silencio ou quebrar na persistencia.
     */
    public static void requireScale(String field, BigDecimal value, int maxScale) {
        if (value != null && value.scale() > maxScale) {
            throw new ForecastValidationException("VALUE_SCALE",
                    field + " com escala " + value.scale() + " acima de " + maxScale + ": " + value);
        }
    }

    static void requireMoney(String field, BigDecimal value) {
        if (value == null) {
            throw new ForecastValidationException("VALUE_REQUIRED",
                    field + " e obrigatorio: ausencia e erro, nunca zero presumido");
        }
        if (value.signum() < 0) {
            throw new ForecastValidationException("VALUE_NEGATIVE",
                    field + " nao pode ser negativo: " + value);
        }
        try {
            value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ForecastValidationException("VALUE_SCALE",
                    field + " deve ter ate 2 casas decimais: " + value);
        }
    }
}
