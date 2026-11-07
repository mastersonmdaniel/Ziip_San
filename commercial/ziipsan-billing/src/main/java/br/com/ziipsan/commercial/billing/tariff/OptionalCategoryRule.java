package br.com.ziipsan.commercial.billing.tariff;

/**
 * Categoria opcional da NR 13 (art. 6º) configurada pelo tenant em
 * {@code tariff.optional_categories} (classe d, P2.5/RAS-13): cada categoria
 * configurada declara o seu <b>critério de enquadramento</b>, a <b>prova</b>
 * exigida e a <b>validade</b> do enquadramento, em meses. Critério, prova e
 * validade são texto/número do tenant — o produto não inventa nenhum deles;
 * a ausência de qualquer um recusa a configuração.
 *
 * @param category       a categoria opcional
 * @param criterion      critério de enquadramento declarado pelo tenant
 * @param proof          prova/documento exigido, declarado pelo tenant
 * @param validityMonths validade do enquadramento, em meses (ao menos 1)
 */
public record OptionalCategoryRule(OptionalCategory category, String criterion, String proof,
                                   int validityMonths) {

    /** Categorias opcionais da NR 13, art. 6º, nomeadas no acceptance da P2.5. */
    public enum OptionalCategory {
        /** Residencial vulnerável. */
        VULNERABLE_RESIDENTIAL,
        /** Micro e pequena empresa. */
        MICRO_SMALL_BUSINESS,
        /** Coletiva com várias economias e hidrômetro único. */
        COLLECTIVE_SINGLE_METER,
        /** Entidade assistencial. */
        ASSISTANCE_ENTITY
    }

    public OptionalCategoryRule {
        if (category == null) {
            throw new TariffConfigurationException("tariff.optional_categories: categoria ausente");
        }
        if (criterion == null || criterion.isBlank()) {
            throw new TariffConfigurationException(
                    "tariff.optional_categories: critério de enquadramento ausente para " + category);
        }
        if (proof == null || proof.isBlank()) {
            throw new TariffConfigurationException(
                    "tariff.optional_categories: prova ausente para " + category);
        }
        if (validityMonths < 1) {
            throw new TariffConfigurationException(
                    "tariff.optional_categories: validade deve ser ao menos 1 mês para " + category
                            + ": " + validityMonths);
        }
    }
}
