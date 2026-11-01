package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billing.math.Apportionment;
import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de tarifa de água e esgoto: domínio puro, sem I/O. Reescrito a partir da especificação do
 * levantamento 02 (seções 3.2, 4.2, 4.3 e 4.4), não do código do legado.
 *
 * <h2>Água (progressiva, por economia)</h2>
 * Com {@code E} = total de economias, {@code mín(c)} = consumo mínimo da categoria {@code c} e
 * {@code C} = consumo faturado do imóvel:
 * <pre>
 *   mínImóvel = Σ mín(c) × economias(c)         excesso = C − mínImóvel
 *   excesso &gt; 0:  cada economia paga a parcela fixa e (excesso DIV E) m³ nas faixas
 *   excesso ≤ 0:  cada economia usa MIN(C DIV E, mín) m³ da parcela fixa, e (C DIV E − mín) nas faixas
 * </pre>
 * {@code DIV} é divisão inteira truncada: o resto não é cobrado por este motor (o ajuste do consumo
 * ao múltiplo de economias é feito antes, em {@code EconomyMultiple}). Cada faixa cobra
 * {@code MIN(excedente, fim − fimAnterior)} m³, a começar do consumo mínimo.
 *
 * <h2>Esgoto</h2>
 * {@code pccoleta} reduz o <b>volume</b> ({@link #sewerVolume}: HALF_UP ao m³); {@code pcesgoto}
 * multiplica o <b>valor</b> calculado com a mesma estrutura de tarifa sobre o volume de esgoto. O
 * fator é arredondado a 2 casas ou exato, conforme a {@link SewerFactorPolicy} explícita.
 *
 * <h2>Mais de uma vigência no período</h2>
 * O cálculo é feito inteiro em cada versão e cada valor é multiplicado pelo fator de dias da versão
 * (dias da versão ÷ dias do período, 4 casas HALF_UP). O último fator é {@code 1 − Σ anteriores}, o
 * que garante que os fatores fechem em 1,0000. O valor de cada categoria é arredondado (HALF_UP, 2
 * casas) uma vez; suas linhas são repartidas por maior resto para somarem exatamente esse valor.
 *
 * <h2>Arredondamento</h2>
 * Linha de faixa: {@code preço × m³ × economias}, HALF_UP, 2 casas. Desconto social: uma única divisão
 * HALF_UP. Nada usa {@code double}.
 */
public final class TariffEngine {

    private static final BigDecimal ONE_4_PLACES = new BigDecimal("1.0000");
    private static final int PRORATION_SCALE = 4;

    private TariffEngine() {
    }

    // ------------------------------------------------------------------------------------------
    // Período completo (vigências + rateio por dias)
    // ------------------------------------------------------------------------------------------

    /**
     * Tarifa de água para o período de leitura {@code [from, to]} (as duas pontas contam).
     *
     * @throws TariffConfigurationException tarifa ausente em algum dia, regra de categoria ausente,
     *                                      faixas que não cobrem o consumo, ou tarifa social pedida
     *                                      sem regra na versão
     */
    public static TariffCharge calculate(TariffSchedule schedule, LocalDate from, LocalDate to,
                                         List<TariffGroup> groups, int consumption) {
        List<TariffSchedule.PeriodSlice> periods = schedule.slices(from, to);
        int totalDays = (int) (ChronoUnit.DAYS.between(from, to) + 1);
        List<SliceCharge> slices = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int i = 0; i < periods.size(); i++) {
            TariffSchedule.PeriodSlice period = periods.get(i);
            int days = (int) (ChronoUnit.DAYS.between(period.from(), period.to()) + 1);
            List<CategoryCharge> categories = categoriesFor(period.version(), groups, consumption);
            BigDecimal factor = ONE_4_PLACES;
            if (periods.size() > 1) {
                if (i < periods.size() - 1) {
                    factor = BigDecimal.valueOf(days)
                            .divide(BigDecimal.valueOf(totalDays), PRORATION_SCALE, RoundingMode.HALF_UP);
                } else {
                    factor = ONE_4_PLACES.subtract(accumulated);
                }
                if (factor.signum() <= 0) {
                    throw new IllegalStateException("Fator de rateio por dias não positivo: " + factor);
                }
                accumulated = accumulated.add(factor);
                categories = scale(categories, factor);
            }
            slices.add(new SliceCharge(period.version().id(), period.from(), period.to(), days, factor, categories));
        }
        return new TariffCharge(from, to, totalDays, slices);
    }

    /**
     * Esgoto para o período: volume reduzido por {@code pccoleta}, tarifa sobre esse volume, valor
     * multiplicado pelo fator de {@code pcesgoto}.
     */
    public static SewerCharge calculateSewer(TariffSchedule schedule, LocalDate from, LocalDate to,
                                             List<TariffGroup> groups, int baseVolume, SewerParameters parameters) {
        requireGroups(groups);
        int volume = sewerVolume(baseVolume, parameters.collectionPct());
        int totalEconomies = groups.stream().mapToInt(TariffGroup::economies).sum();

        BigDecimal effectivePct = parameters.sewerPct();
        SewerParameters.AlternativeSewerPct alternative = parameters.alternative();
        if (alternative != null && volume / totalEconomies <= alternative.maxVolumePerEconomy()
                && alternative.pct().compareTo(effectivePct) < 0) {
            effectivePct = alternative.pct();
        }
        BigDecimal factor = switch (parameters.factorPolicy()) {
            case ROUNDED_TO_2_DECIMALS -> effectivePct.divide(Money.HUNDRED, 2, RoundingMode.HALF_UP);
            case EXACT -> effectivePct.movePointLeft(2);
        };

        List<TariffGroup> effectiveGroups = parameters.applySocialDiscount()
                ? groups
                : groups.stream().map(g -> new TariffGroup(g.categoryId(), g.subcategoryId(), g.economies(), 0)).toList();
        TariffCharge structure = calculate(schedule, from, to, effectiveGroups, volume);

        List<SliceCharge> scaled = new ArrayList<>();
        for (SliceCharge s : structure.slices()) {
            scaled.add(new SliceCharge(s.tariffVersionId(), s.from(), s.to(), s.days(), s.factor(),
                    scale(s.categories(), factor)));
        }
        return new SewerCharge(baseVolume, volume, effectivePct, factor,
                new TariffCharge(structure.from(), structure.to(), structure.totalDays(), scaled));
    }

    /**
     * Volume de esgoto: {@code baseVolume × pccoleta / 100}, arredondado ao m³ com
     * {@link RoundingMode#HALF_UP} (27 × 80% = 21,6 → 22; 25 × 70% = 17,5 → 18).
     */
    public static int sewerVolume(int baseVolume, BigDecimal collectionPct) {
        if (baseVolume < 0) {
            throw new IllegalArgumentException("Volume negativo: " + baseVolume);
        }
        return BigDecimal.valueOf(baseVolume).multiply(collectionPct)
                .divide(Money.HUNDRED, 0, RoundingMode.HALF_UP).intValueExact();
    }

    // ------------------------------------------------------------------------------------------
    // Uma versão de tarifa
    // ------------------------------------------------------------------------------------------

    /**
     * Consumo mínimo da ligação (UC0105): {@code Σ mín(c) × (fatorEconomias(c) ?? economias(c))}. O
     * fator, quando existe, <b>substitui</b> o número de economias. Total arredondado ao m³ (HALF_UP).
     */
    public static int minimumConsumption(TariffVersion version, List<TariffGroup> groups) {
        requireGroups(groups);
        BigDecimal total = BigDecimal.ZERO;
        for (TariffGroup g : groups) {
            CategoryRule rule = version.ruleFor(g.categoryId(), g.subcategoryId());
            BigDecimal economies = rule.economyFactor() != null
                    ? rule.economyFactor()
                    : BigDecimal.valueOf(g.economies());
            total = total.add(BigDecimal.valueOf(rule.minConsumption()).multiply(economies));
        }
        return total.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /**
     * Cálculo de uma única versão, sem rateio por dias: uma {@link CategoryCharge} por grupo.
     * O número de economias usado no excedente é o real (o fator de economias não entra aqui).
     */
    public static List<CategoryCharge> categoriesFor(TariffVersion version, List<TariffGroup> groups, int consumption) {
        requireGroups(groups);
        if (consumption < 0) {
            throw new IllegalArgumentException("Consumo negativo: " + consumption);
        }
        int totalEconomies = groups.stream().mapToInt(TariffGroup::economies).sum();
        List<CategoryRule> rules = new ArrayList<>();
        long propertyMinimum = 0;
        for (TariffGroup g : groups) {
            if (g.socialEconomies() > 0 && version.socialRule() == null) {
                throw new TariffConfigurationException("Há economias na tarifa social, mas a versão " + version.id()
                        + " não tem regra de tarifa social");
            }
            CategoryRule rule = version.ruleFor(g.categoryId(), g.subcategoryId());
            rules.add(rule);
            propertyMinimum += (long) rule.minConsumption() * g.economies();
        }
        long excess = consumption - propertyMinimum;
        int excessPerEconomy = excess > 0 ? (int) (excess / totalEconomies) : 0;
        int consumptionPerEconomy = consumption / totalEconomies;

        List<CategoryCharge> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            TariffGroup g = groups.get(i);
            CategoryRule rule = rules.get(i);
            int minPerEconomy = rule.minConsumption();
            int minBilledPerEconomy;
            int remaining;
            if (excess > 0) {
                minBilledPerEconomy = minPerEconomy;
                remaining = excessPerEconomy;
            } else {
                minBilledPerEconomy = Math.min(consumptionPerEconomy, minPerEconomy);
                remaining = consumptionPerEconomy - minPerEconomy;
            }
            result.add(categoryCharge(version, g, rule, minBilledPerEconomy, remaining));
        }
        return result;
    }

    private static CategoryCharge categoryCharge(TariffVersion version, TariffGroup g, CategoryRule rule,
                                                 int minBilledPerEconomy, int remaining) {
        int n = g.economies();
        int social = g.socialEconomies();
        SocialTariffRule socialRule = version.socialRule();
        List<ChargeLine> lines = new ArrayList<>();
        int consumption = minBilledPerEconomy * n;

        // parcela fixa
        BigDecimal minGross = rule.minCharge().multiply(BigDecimal.valueOf(n)).setScale(Money.SCALE, RoundingMode.HALF_UP);
        BigDecimal minDiscount = social > 0
                ? Money.percentOf(rule.minCharge().multiply(BigDecimal.valueOf(social)),
                        socialRule.minimumChargeDiscountPct(), RoundingMode.HALF_UP)
                : Money.ZERO;
        lines.add(new ChargeLine(ChargeLine.Type.MINIMUM, 0, rule.minConsumption(), null,
                minBilledPerEconomy * n, minGross, minDiscount));

        // faixas progressivas
        int segmentStart = rule.minConsumption();
        int left = remaining;
        for (TariffBlock block : rule.blocks()) {
            if (left <= 0) {
                break;
            }
            Integer end = block.toM3();
            if (end != null && end <= segmentStart) {
                continue; // faixa inteira abaixo do consumo mínimo
            }
            int quantity = end == null ? left : Math.min(left, end - segmentStart);
            BigDecimal gross = block.pricePerM3().multiply(BigDecimal.valueOf((long) quantity * n))
                    .setScale(Money.SCALE, RoundingMode.HALF_UP);
            BigDecimal discount = Money.ZERO;
            if (social > 0) {
                BigDecimal weight = socialRule.weightedOverlap(segmentStart, segmentStart + quantity);
                discount = block.pricePerM3().multiply(BigDecimal.valueOf(social)).multiply(weight)
                        .divide(Money.HUNDRED, Money.SCALE, RoundingMode.HALF_UP);
            }
            lines.add(new ChargeLine(ChargeLine.Type.BLOCK, segmentStart, end, block.pricePerM3(),
                    quantity * n, gross, discount));
            consumption += quantity * n;
            left -= quantity;
            segmentStart += quantity;
        }
        if (left > 0) {
            throw new TariffConfigurationException("As faixas da categoria " + rule.categoryId()
                    + " não cobrem o consumo: faltam " + left + " m³ por economia (a última faixa deve ser aberta)");
        }
        return new CategoryCharge(g.categoryId(), g.subcategoryId(), n, social, consumption, lines);
    }

    // ------------------------------------------------------------------------------------------
    // Escala (rateio por dias e fator de esgoto)
    // ------------------------------------------------------------------------------------------

    /**
     * Multiplica os valores de cada categoria por {@code factor}. O valor da categoria (bruto e
     * desconto) é arredondado uma vez (HALF_UP); as linhas são repartidas por maior resto para
     * somarem exatamente esse valor.
     */
    private static List<CategoryCharge> scale(List<CategoryCharge> categories, BigDecimal factor) {
        List<CategoryCharge> scaled = new ArrayList<>();
        for (CategoryCharge c : categories) {
            BigDecimal grossTarget = Money.roundHalfUp(c.gross().multiply(factor));
            BigDecimal discountTarget = Money.roundHalfUp(c.discount().multiply(factor));
            List<BigDecimal> gross = Apportionment.proportional(grossTarget,
                    c.lines().stream().map(l -> cents(l.gross())).toList());
            List<BigDecimal> discount = Apportionment.proportional(discountTarget,
                    c.lines().stream().map(l -> cents(l.discount())).toList());
            List<ChargeLine> lines = new ArrayList<>();
            for (int i = 0; i < c.lines().size(); i++) {
                ChargeLine l = c.lines().get(i);
                lines.add(new ChargeLine(l.type(), l.fromM3(), l.toM3(), l.pricePerM3(), l.consumption(),
                        gross.get(i), discount.get(i)));
            }
            scaled.add(new CategoryCharge(c.categoryId(), c.subcategoryId(), c.economies(), c.socialEconomies(),
                    c.consumption(), lines));
        }
        return scaled;
    }

    private static long cents(BigDecimal value) {
        return value.movePointRight(2).longValueExact();
    }

    private static void requireGroups(List<TariffGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("A unidade não tem economias");
        }
    }
}
