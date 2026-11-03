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

    /**
     * Padrão de fábrica da multa moratória: 2% (convergência de 10 ERIs, PARAMETROS G5).
     * É o valor aplicado quando o tenant não declara outro; a trava legal é a mesma, mas
     * os conceitos são distintos: padrão ≠ teto.
     */
    public static final BigDecimal FACTORY_FINE_PERCENT = new BigDecimal("2.00");

    /**
     * Referência documentada, NÃO trava global: teto de juros de mora de Santa Catarina,
     * 0,033% ao dia (classe d; os tetos por agência chegam nos perfis da P2.31).
     */
    public static final BigDecimal SC_INTEREST_DAILY_PERCENT = new BigDecimal("0.033");

    /**
     * Metodologia da taxa legal de fallback (P0.20, {@code fee.legal_rate_fallback}):
     * Resolução CMN 5.171/2024, taxa divulgada mensalmente pelo BCB. Nenhum valor de
     * taxa é fixado aqui — a carga mensal do BCB fica fora do P0.20.
     */
    public static final String LEGAL_RATE_METHODOLOGY =
            "Resolução CMN 5.171/2024; taxa divulgada mensalmente pelo BCB";

    private LegalLimits() {
    }
}
