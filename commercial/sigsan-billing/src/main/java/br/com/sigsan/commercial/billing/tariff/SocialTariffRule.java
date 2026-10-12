package br.com.sigsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Regra de cálculo da tarifa social de uma versão de tarifa: <b>todos os valores são parâmetro da
 * tabela tarifária do tenant</b>; nenhum percentual ou limite de volume existe no código.
 *
 * <p>O desconto incide por volume: para cada faixa de preço da conta, os m³ que caem dentro de uma
 * {@link DiscountBand} recebem o percentual daquela banda. Isso cobre, só com parâmetros, desenhos
 * diferentes de agência: uma banda de 0 a N m³ com um percentual; duas bandas escalonadas; uma banda
 * sem limite de volume.
 *
 * <p>A parcela fixa (tarifa mínima) tem percentual próprio, informado de forma explícita (pode ser
 * zero): algumas agências estendem o desconto à parte fixa, outras não.
 *
 * <p>O que esta classe NÃO decide: quem é elegível (CadÚnico, BPC, uma economia por família), porque
 * isso é cadastro e dado sensível, nem a perda do benefício. A regra é aplicada às economias que o
 * chamador marca como sociais; a concessão não consulta adimplência (Lei 14.898/2024, relatório 08).
 *
 * @param minimumChargeDiscountPct desconto sobre a parcela fixa, em [0, 100]
 * @param bands                    bandas em ordem crescente e sem sobreposição
 */
public record SocialTariffRule(BigDecimal minimumChargeDiscountPct, List<DiscountBand> bands) {

    public SocialTariffRule {
        if (minimumChargeDiscountPct == null
                || minimumChargeDiscountPct.signum() < 0
                || minimumChargeDiscountPct.compareTo(new BigDecimal("100")) > 0) {
            throw new TariffConfigurationException(
                    "Desconto sobre a parcela fixa deve ser informado, em [0, 100] (use 0 se não houver): "
                            + minimumChargeDiscountPct);
        }
        if (bands == null || bands.isEmpty()) {
            throw new TariffConfigurationException("Regra de tarifa social sem nenhuma banda de desconto");
        }
        bands = bands.stream().sorted(Comparator.comparingInt(DiscountBand::fromM3)).toList();
        for (int i = 1; i < bands.size(); i++) {
            Integer previousEnd = bands.get(i - 1).toM3();
            if (previousEnd == null || previousEnd > bands.get(i).fromM3()) {
                throw new TariffConfigurationException("Bandas de desconto se sobrepõem");
            }
        }
    }

    /** Soma de (m³ da banda dentro de {@code (lo, hi]}) × percentual da banda. */
    BigDecimal weightedOverlap(int lo, int hi) {
        BigDecimal weight = BigDecimal.ZERO;
        for (DiscountBand band : bands) {
            int bandEnd = band.toM3() == null ? Integer.MAX_VALUE : band.toM3();
            int overlap = Math.min(hi, bandEnd) - Math.max(lo, band.fromM3());
            if (overlap > 0) {
                weight = weight.add(band.discountPct().multiply(BigDecimal.valueOf(overlap)));
            }
        }
        return weight;
    }
}
