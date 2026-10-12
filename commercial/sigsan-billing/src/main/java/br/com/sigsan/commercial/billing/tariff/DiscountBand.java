package br.com.sigsan.commercial.billing.tariff;

import java.math.BigDecimal;

/**
 * Faixa de volume com desconto da tarifa social: vale para os m³ de {@code (fromM3, toM3]} por
 * economia (o primeiro m³ da faixa é {@code fromM3 + 1}).
 *
 * @param fromM3      início exclusivo (0 = desde o primeiro m³)
 * @param toM3        fim inclusivo; {@code null} = sem limite de volume
 * @param discountPct percentual de desconto sobre o preço do m³, em (0, 100]
 */
public record DiscountBand(int fromM3, Integer toM3, BigDecimal discountPct) {

    public DiscountBand {
        if (fromM3 < 0) {
            throw new TariffConfigurationException("Início da faixa de desconto negativo: " + fromM3);
        }
        if (toM3 != null && toM3 <= fromM3) {
            throw new TariffConfigurationException("Fim da faixa de desconto deve ser maior que o início");
        }
        if (discountPct == null || discountPct.signum() <= 0 || discountPct.compareTo(new BigDecimal("100")) > 0) {
            throw new TariffConfigurationException("Percentual de desconto deve estar em (0, 100]: " + discountPct);
        }
    }
}
