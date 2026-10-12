package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Os dois arredondamentos para número inteiro de m³ usados pela cascata. Cada regra escolhe um
 * deles de forma explícita, e a escolha está documentada na própria regra.
 *
 * <p>O volume faturado é inteiro (m³ cheios), então toda operação que produz fração termina aqui.
 * Nada é arredondado no meio de uma conta: as frações intermediárias ficam em {@link BigDecimal}
 * com a escala indicada na regra.
 */
final class Rounding {

    private Rounding() {
    }

    /** "ARRED" do relatório: {@link RoundingMode#HALF_UP}. 12,5 vira 13; -5,7145 vira -6. */
    static int halfUp(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** "TRUNCA" do relatório: {@link RoundingMode#DOWN}, em direção a zero. 20,83 vira 20. */
    static int truncate(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN).intValueExact();
    }
}
