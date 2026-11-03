package br.com.ziipsan.commercial.billing.social;

import br.com.ziipsan.commercial.billing.tariff.DiscountBand;
import br.com.ziipsan.commercial.billing.tariff.SocialTariffRule;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.math.BigDecimal;

/**
 * Piso federal da tarifa social (P1.5; Lei 14.898/2024; NR 13/ANA; catalogo de parametros
 * {@code social.variable_bands}): desconto de 50% ate 15 m3. Uma regra de tarifa social
 * cuja cobertura de (0, 15] fique abaixo disso e recusada: beneficio abaixo do piso
 * federal nao pode ser gravado como politica do tenant.
 *
 * <p>So o volume e validado aqui. O desconto sobre a parcela fixa nao tem piso confirmado
 * (catalogo: "piso federal a confirmar na Lei 14.898/NR 13 e na norma da agencia (G2)"),
 * entao nao e travado: qualquer valor em [0, 100] ja validado por {@link SocialTariffRule}
 * passa. A confirmacao no DOU do piso (catalogo, antes da ativacao em producao) continua
 * pendente: se o DOU divergir destes 50% / 15 m3, mudam as constantes abaixo, nao a forma
 * da validacao.
 */
public final class SocialBenefitFloor {

    /** Piso: 50% de desconto. */
    public static final BigDecimal FLOOR_PCT = new BigDecimal("50");
    /** Piso: ate 15 m3 por economia. */
    public static final int FLOOR_M3 = 15;

    private SocialBenefitFloor() {
    }

    /** Lanca {@link TariffConfigurationException} se a regra ficar abaixo do piso federal. */
    public static void validate(SocialTariffRule rule) {
        if (rule == null) {
            throw new TariffConfigurationException("Regra de tarifa social ausente: sem regra nao ha piso a validar");
        }
        int cursor = 0;
        for (DiscountBand band : rule.bands()) {
            int bandEnd = band.toM3() == null ? Integer.MAX_VALUE : band.toM3();
            if (band.fromM3() > cursor) {
                throw new TariffConfigurationException(
                        "Beneficio abaixo do piso federal: sem desconto de (0, 15] m3 em " + cursor + " m3");
            }
            if (bandEnd > cursor && bandEnd > 0) {
                int overlapEnd = Math.min(bandEnd, FLOOR_M3);
                if (overlapEnd > cursor && band.discountPct().compareTo(FLOOR_PCT) < 0) {
                    throw new TariffConfigurationException(
                            "Beneficio abaixo do piso federal: desconto de " + band.discountPct()
                                    + "% ate " + overlapEnd + " m3, abaixo de 50% ate 15 m3");
                }
                cursor = Math.max(cursor, overlapEnd);
            }
            if (cursor >= FLOOR_M3) {
                return;
            }
        }
        throw new TariffConfigurationException(
                "Beneficio abaixo do piso federal: cobertura termina em " + cursor + " m3, abaixo de 15 m3");
    }
}
