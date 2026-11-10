package br.com.ziipsan.commercial.billingrules.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Aritmética monetária do faturamento. Todo valor em reais tem escala 2; todo arredondamento escolhe
 * o {@link RoundingMode} de forma explícita (nunca o padrão implícito).
 *
 * <p>Regras de arredondamento usadas no módulo (cada uma documentada onde é aplicada):
 * <ul>
 *   <li>{@link RoundingMode#HALF_UP}: valores de tarifa, esgoto, rateio por dias, juros e multa;</li>
 *   <li>{@link RoundingMode#DOWN}: parcela de débito e de crédito (os centavos sobram para a
 *       última parcela) e base de cálculo de imposto retido;</li>
 *   <li>{@link RoundingMode#HALF_DOWN}: imposto retido (herdado do levantamento do legado; nenhuma
 *       norma lida define arredondamento, ver README).</li>
 * </ul>
 *
 * <p>Atenção: nunca construir {@code BigDecimal} a partir de {@code double}
 * ({@code new BigDecimal(0.7)} vale 0,6999999999999999555...). Valores decimais entram como
 * {@code String} ou como inteiro com escala.
 */
public final class Money {

    /** Escala monetária (centavos). */
    public static final int SCALE = 2;

    /** Zero com escala monetária. */
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    /** Arredonda para centavos com {@link RoundingMode#HALF_UP}. */
    public static BigDecimal roundHalfUp(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Trunca para centavos ({@link RoundingMode#DOWN}, em direção a zero). */
    public static BigDecimal truncate(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.DOWN);
    }

    /** Valor com escala monetária a partir de texto decimal ("12.5" vira 12.50). Falha se perder precisão. */
    public static BigDecimal of(String decimal) {
        return new BigDecimal(decimal).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** {@code amount × pct / 100}, uma única divisão (e um único arredondamento) com o modo dado. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal pct, RoundingMode mode) {
        return amount.multiply(pct).divide(HUNDRED, SCALE, mode);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
