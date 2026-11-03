package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;

/**
 * Elegibilidade para a tarifa social (P1.5; Lei 14.898/2024; NR 13/ANA).
 *
 * <p>Regras implementadas, todas sem nenhum dado pessoal em claro (o cruzamento usa
 * apenas indices cegos; esta classe recebe datas e bases, nunca CPF, NIS ou renda):
 * <ul>
 *   <li>{@code CADUNICO}: elegivel se a atualizacao do CadUnico nao for mais antiga que
 *       {@code social.cadunico_max_age_months} (24) na data de referencia. Uma atualizacao
 *       de 25 meses e inelegivel; uma de exatamente 24 meses ainda e elegivel.</li>
 *   <li>{@code BPC} e {@code LOCAL_PROGRAM}: elegiveis sem data de CadUnico; a base BPC
 *       da direito por si (relatorio 08, R-14) e o programa local segue a norma da ERI.</li>
 *   <li>Nenhuma entrada de adimplencia existe nesta classe, por desenho: a concessao nao
 *       pode ser condicionada a pagamento (Lei 14.898/2024).</li>
 * </ul>
 */
public final class SocialEligibility {

    /** Bases de elegibilidade de {@code social_tariff_enrollments.eligibility_basis}. */
    public enum Basis {
        CADUNICO, BPC, LOCAL_PROGRAM
    }

    private SocialEligibility() {
    }

    /**
     * @param basis             base de elegibilidade declarada na inscricao
     * @param cadunicoUpdatedOn data da ultima atualizacao do CadUnico; obrigatoria
     *                          exatamente quando a base e CADUNICO (o banco impoe isso)
     * @param referenceDate     data em que a elegibilidade e avaliada (competencia)
     * @param cadunicoMaxAgeMonths {@code social.cadunico_max_age_months} (24; so amplia)
     */
    public static boolean isEligible(Basis basis, LocalDate cadunicoUpdatedOn,
            LocalDate referenceDate, int cadunicoMaxAgeMonths) {
        if (basis == null || referenceDate == null) {
            throw new IllegalArgumentException("Base de elegibilidade e data de referencia sao obrigatorias");
        }
        if (cadunicoMaxAgeMonths < 0) {
            throw new IllegalArgumentException("Idade maxima do CadUnico nao pode ser negativa");
        }
        return switch (basis) {
            case BPC, LOCAL_PROGRAM -> true;
            case CADUNICO -> cadunicoUpdatedOn != null
                    && !cadunicoUpdatedOn.plusMonths(cadunicoMaxAgeMonths).isBefore(referenceDate);
        };
    }
}
