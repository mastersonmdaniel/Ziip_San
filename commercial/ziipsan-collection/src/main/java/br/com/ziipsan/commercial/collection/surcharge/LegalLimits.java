package br.com.ziipsan.commercial.collection.surcharge;

import java.math.BigDecimal;

/**
 * Travas legais que o código impõe quando o tenant não declara outra norma. São <b>limites</b>,
 * nunca valores aplicados: o percentual de multa de um tenant vem do contrato/regulador dele e é
 * recusado se ultrapassar a trava.
 */
public final class LegalLimits {

    /**
     * Multa moratória máxima, em percentual do valor da prestação: 2%. Fonte: CDC (Lei 8.078/1990)
     * art. 52 §1º, com a redação da Lei 9.298/1996. Se o art. 52 alcança a tarifa de serviço
     * público é ponto aberto para o jurídico (relatório 11, pergunta 9); reguladores como a
     * ARSESP adotam 2%. Um tenant cuja norma permita mais deve declarar o limite próprio e a
     * justificativa em {@link FineRule#withCapOverride}.
     */
    public static final BigDecimal MAX_FINE_PERCENT = new BigDecimal("2.00");

    private LegalLimits() {
    }
}
