package br.com.sigsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parâmetros da cobrança de esgoto de uma ligação. Nenhum campo tem valor padrão: o percentual de
 * esgoto, o de coleta, a política do fator e a aplicação do desconto social são decisões do
 * prestador/agência.
 *
 * @param sewerPct              {@code pcesgoto}: percentual do valor da tarifa (multiplica o <b>valor</b>)
 * @param collectionPct         {@code pccoleta}: percentual da água consumida que é coletada (reduz o <b>volume</b>)
 * @param alternative           percentual menor para consumo baixo; {@code null} = a ligação não tem
 * @param factorPolicy          como {@code sewerPct} vira fator
 * @param applySocialDiscount   se o desconto da tarifa social também reduz o esgoto. <b>Não confirmado</b>
 *                              em norma lida (levantamento 08); decisão do tenant
 */
public record SewerParameters(BigDecimal sewerPct, BigDecimal collectionPct, AlternativeSewerPct alternative,
                              SewerFactorPolicy factorPolicy, boolean applySocialDiscount) {

    /**
     * Percentual alternativo: vale quando o volume de esgoto por economia é no máximo
     * {@code maxVolumePerEconomy}, e só se for menor que o percentual normal.
     */
    public record AlternativeSewerPct(BigDecimal pct, int maxVolumePerEconomy) {
        public AlternativeSewerPct {
            validatePct(pct, "percentual alternativo de esgoto");
            if (maxVolumePerEconomy < 0) {
                throw new IllegalArgumentException("Volume máximo do percentual alternativo negativo");
            }
        }
    }

    public SewerParameters {
        validatePct(sewerPct, "percentual de esgoto (pcesgoto)");
        validatePct(collectionPct, "percentual de coleta (pccoleta)");
        Objects.requireNonNull(factorPolicy, "política do fator de esgoto");
    }

    private static void validatePct(BigDecimal pct, String name) {
        if (pct == null || pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("O " + name + " deve ser informado, em [0, 100]: " + pct);
        }
    }
}
