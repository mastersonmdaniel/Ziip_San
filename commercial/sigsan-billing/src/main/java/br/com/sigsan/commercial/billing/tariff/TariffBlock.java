package br.com.sigsan.commercial.billing.tariff;

import java.math.BigDecimal;

/**
 * Faixa de consumo progressiva: o preço vale para os m³ que ficam entre o fim da faixa anterior (ou
 * o consumo mínimo) e {@code toM3}, inclusive.
 *
 * @param toM3       último m³ da faixa, por economia; {@code null} = faixa aberta (só a última pode ser)
 * @param pricePerM3 preço do m³ na faixa (aceita mais de 2 casas; o valor da linha é arredondado)
 */
public record TariffBlock(Integer toM3, BigDecimal pricePerM3) {

    public TariffBlock {
        if (pricePerM3 == null) {
            throw new TariffConfigurationException("Preço por m³ ausente na faixa");
        }
        if (pricePerM3.signum() < 0) {
            throw new TariffConfigurationException("Preço por m³ negativo: " + pricePerM3);
        }
        if (toM3 != null && toM3 <= 0) {
            throw new TariffConfigurationException("Fim de faixa deve ser positivo: " + toM3);
        }
    }
}
