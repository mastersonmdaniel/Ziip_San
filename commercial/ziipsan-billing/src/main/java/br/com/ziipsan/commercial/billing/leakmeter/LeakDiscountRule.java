package br.com.ziipsan.commercial.billing.leakmeter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Calculo do desconto por vazamento oculto (P3.28): dados o volume medido e
 * a media da unidade (agua e esgoto), produz os volumes REVISTOS que o
 * chamador entrega a retificacao da P2.20. O recalculo tarifario em si
 * (faixas, tarifa) e do motor da P2.5 -- esta regra so decide volumes,
 * como a P2.20 recebe {@code RectifiedValues} prontos.
 *
 * <p>Regras (PARAMETROS-DO-TENANT, linha {@code leak.*}; 20b §5.1):
 * <ul>
 *   <li><b>PCT_OF_EXCESS</b> -- desconto de {@code leak.max_discount_pct_water}
 *       sobre o volume de agua ACIMA da media (a formula atestada nas tres
 *       ERIs lidas: "desconto de ate 70% do volume acima da media"). O
 *       excesso nunca e negativo: medido abaixo da media nao gera desconto
 *       nem acrescimo por esta regra. O esgoto segue
 *       {@code leak.sewer_by_average} (revisto = media) ou, desligado ele,
 *       o mesmo desenho percentual com
 *       {@code leak.max_discount_pct_sewer} sobre o excesso de esgoto;</li>
 *   <li><b>AVERAGE_6M</b> -- fatura-se pela media: volume revisto = media,
 *       agua e esgoto ("esgoto pela media" e inerente a esta regra). E uma
 *       BASE de faturamento, nao um desconto: se o medido esta abaixo da
 *       media, o revisto e a media mesmo assim -- e por isso o padrao sem
 *       perfil e MOST_FAVORABLE, que protege o usuario dessa ponta;</li>
 *   <li><b>MOST_FAVORABLE</b> -- calcula os dois ramos e fica com o de MENOR
 *       volume total revisto (agua + esgoto), o mais favoravel ao usuario;
 *       empate resolve para AVERAGE_6M (deterministico).</li>
 * </ul>
 *
 * <p>Aritmetica exata: o desconto percentual e
 * {@code excesso * pct / 100} por deslocamento decimal
 * ({@link BigDecimal#movePointLeft}), sem arredondamento -- 30 m3 a 70%
 * sao exatamente 21 m3. O arredondamento para a precisao da conta e do
 * motor de faturamento, nao desta regra.
 */
public final class LeakDiscountRule {

    /** Volumes de entrada, em m3: medidos na fatura e medias da unidade. */
    public record Volumes(BigDecimal measuredWater, BigDecimal averageWater,
                          BigDecimal measuredSewer, BigDecimal averageSewer) {
        public Volumes {
            requireNonNegative("measuredWater", measuredWater);
            requireNonNegative("averageWater", averageWater);
            requireNonNegative("measuredSewer", measuredSewer);
            requireNonNegative("averageSewer", averageSewer);
        }

        private static void requireNonNegative(String field, BigDecimal value) {
            if (value == null || value.signum() < 0) {
                throw new IllegalArgumentException("Volume " + field + " obrigatorio e nao negativo: " + value);
            }
        }
    }

    /**
     * Volumes revistos e os descontos concedidos (medido - revisto; em
     * AVERAGE_6M com medido abaixo da media o "desconto" e negativo, porque
     * a regra e base de faturamento -- ver cabecalho). {@code effectiveRule}
     * e o ramo que produziu o resultado (em MOST_FAVORABLE, o ramo vencedor).
     */
    public record Result(BigDecimal revisedWater, BigDecimal revisedSewer,
                         BigDecimal waterDiscount, BigDecimal sewerDiscount,
                         LeakMeterParameters.DiscountRule effectiveRule) {
    }

    private LeakDiscountRule() {
    }

    public static Result apply(LeakMeterParameters parameters, Volumes volumes) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(volumes, "volumes");
        return switch (parameters.discountRule()) {
            case AVERAGE_6M -> byAverage(volumes);
            case PCT_OF_EXCESS -> byPercentOfExcess(parameters, volumes);
            case MOST_FAVORABLE -> {
                Result average = byAverage(volumes);
                Result percent = byPercentOfExcess(parameters, volumes);
                BigDecimal averageTotal = average.revisedWater().add(average.revisedSewer());
                BigDecimal percentTotal = percent.revisedWater().add(percent.revisedSewer());
                yield percentTotal.compareTo(averageTotal) < 0 ? percent : average;
            }
        };
    }

    /** Ramo AVERAGE_6M: revisto = media, agua e esgoto. */
    static Result byAverage(Volumes volumes) {
        return new Result(volumes.averageWater(), volumes.averageSewer(),
                volumes.measuredWater().subtract(volumes.averageWater()),
                volumes.measuredSewer().subtract(volumes.averageSewer()),
                LeakMeterParameters.DiscountRule.AVERAGE_6M);
    }

    /** Ramo PCT_OF_EXCESS: percentual sobre o excesso de cada volume. */
    static Result byPercentOfExcess(LeakMeterParameters parameters, Volumes volumes) {
        BigDecimal revisedWater = discountExcess(volumes.measuredWater(), volumes.averageWater(),
                parameters.maxDiscountPctWater());
        BigDecimal revisedSewer;
        if (parameters.sewerByAverage()) {
            revisedSewer = volumes.averageSewer();
        } else {
            revisedSewer = discountExcess(volumes.measuredSewer(), volumes.averageSewer(),
                    parameters.maxDiscountPctSewer());
        }
        return new Result(revisedWater, revisedSewer,
                volumes.measuredWater().subtract(revisedWater),
                volumes.measuredSewer().subtract(revisedSewer),
                LeakMeterParameters.DiscountRule.PCT_OF_EXCESS);
    }

    /** Medido menos o desconto de {@code pct}% sobre o excesso acima da media (excesso >= 0). */
    private static BigDecimal discountExcess(BigDecimal measured, BigDecimal average, BigDecimal pct) {
        BigDecimal excess = measured.subtract(average);
        if (excess.signum() <= 0) {
            return measured;
        }
        BigDecimal discount = excess.multiply(pct).movePointLeft(2);
        return measured.subtract(discount);
    }
}
