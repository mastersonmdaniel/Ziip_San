package br.com.ziipsan.commercial.billing.tariff;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Trava de publicação do reajuste (P2.29; NR 10 da ANA; PARAMETROS-DO-TENANT
 * G1, {@code bill.tariff_readjust}): a vigência começa <b>no mínimo 30 dias
 * depois da publicação</b>. O mínimo é travado do produto: o parâmetro do
 * tenant ({@code publicationMinDays} do objeto, ou a regra da agência) pode
 * exigir antecedência <b>maior</b>, nunca menor — pedir 10 dias não afrouxa
 * a trava, vale 30.
 *
 * <p>A mesma trava existe no banco para as linhas novas de reajuste/revisão
 * ({@code chk_tariff_versions_publication_30d}, V4_9). Aqui ela é testável
 * sem banco e vale também para a proposta ainda não persistida (a simulação
 * e a comparação trabalham sobre a proposta em memória).
 */
public final class TariffPublicationRule {

    /** Piso travado de antecedência da publicação, em dias. */
    public static final int MIN_PUBLICATION_DAYS = 30;

    private TariffPublicationRule() {
    }

    /** Antecedência efetiva: o maior entre o piso travado e o parâmetro. */
    public static int effectiveMinDays(int configuredMinDays) {
        return Math.max(MIN_PUBLICATION_DAYS, configuredMinDays);
    }

    /**
     * Exige publicação e antecedência mínima até a vigência.
     *
     * @param publishedOn     data da publicação; obrigatória para reajuste e
     *                        revisão (sem publicação não há antecedência a medir)
     * @param validFrom       início da vigência proposta
     * @param configuredMinDays antecedência do parâmetro/agência; valores
     *                        abaixo de {@link #MIN_PUBLICATION_DAYS} são
     *                        elevados ao piso, nunca aplicados
     * @throws TariffConfigurationException publicação ausente, vigência
     *                        anterior à publicação ou antecedência abaixo do
     *                        mínimo efetivo
     */
    public static void validate(LocalDate publishedOn, LocalDate validFrom, int configuredMinDays) {
        if (validFrom == null) {
            throw new TariffConfigurationException("Vigência do reajuste/revisão sem data de início");
        }
        if (publishedOn == null) {
            throw new TariffConfigurationException(
                    "Reajuste/revisão sem data de publicação: a antecedência mínima de "
                            + MIN_PUBLICATION_DAYS + " dias não pode ser conferida");
        }
        int minDays = effectiveMinDays(configuredMinDays);
        long days = ChronoUnit.DAYS.between(publishedOn, validFrom);
        if (days < minDays) {
            throw new TariffConfigurationException(
                    "Publicação a " + days + " dias da vigência (" + publishedOn + " -> " + validFrom
                            + "): o mínimo é " + minDays + " dias (piso travado de "
                            + MIN_PUBLICATION_DAYS + " dias, NR 10)");
        }
    }
}
