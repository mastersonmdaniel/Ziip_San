package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;

/**
 * Uma linha do cálculo da tarifa de uma categoria: a parcela fixa ou uma faixa de consumo.
 *
 * <p>Valores já nas economias da categoria (preço × m³ × economias), com 2 casas. O desconto da
 * tarifa social vem separado do valor bruto para que a conta mostre os dois.
 *
 * @param fromM3      início da faixa por economia (0 para a parcela fixa)
 * @param toM3        fim da faixa por economia; {@code null} = faixa aberta. Na parcela fixa, o consumo
 *                    mínimo coberto
 * @param pricePerM3  preço do m³; {@code null} na parcela fixa
 * @param consumption m³ atribuídos à linha, somados todas as economias da categoria
 */
public record ChargeLine(Type type, int fromM3, Integer toM3, BigDecimal pricePerM3, int consumption,
                         BigDecimal gross, BigDecimal discount) {

    public enum Type { MINIMUM, BLOCK }

    /** Valor líquido da linha: bruto menos desconto social. */
    public BigDecimal net() {
        return gross.subtract(discount);
    }
}
