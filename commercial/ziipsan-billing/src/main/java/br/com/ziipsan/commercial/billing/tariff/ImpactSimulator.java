package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billingrules.math.Money;
import br.com.ziipsan.commercial.billingrules.tariff.CategoryCharge;
import br.com.ziipsan.commercial.billingrules.tariff.ChargeLine;
import br.com.ziipsan.commercial.billingrules.tariff.SewerCharge;
import br.com.ziipsan.commercial.billingrules.tariff.SliceCharge;
import br.com.ziipsan.commercial.billingrules.tariff.TariffCharge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simulação de impacto da tarifa proposta sobre a base real, <b>antes</b> da
 * vigência (P2.29): para a mesma massa de unidades e consumos, calcula a
 * conta de água e esgoto com a estrutura vigente e com a proposta, pelo
 * <b>mesmo</b> {@link TariffEngine} que a P2.5 usa na conta — por isso a
 * simulação com a proposta igual à vigente reproduz exatamente a conta da
 * P2.5 para a mesma massa (provado em teste contra {@code BillCalculator}).
 *
 * <p><b>Leitura que não grava.</b> Esta classe é pura: não tem repositório,
 * não recebe entidade JPA e não devolve nada persistível. A massa entra como
 * valores ({@link SimulationUnit}) e o relatório sai como valores
 * ({@link ImpactReport}). A vigência não entra no cálculo: vigente e
 * proposta são avaliadas sobre o mesmo período de referência, cada uma
 * redatada para esse período numa cópia de cálculo — simula-se a
 * <i>estrutura</i>, não o calendário. Gravar um registro da simulação, se o
 * chamador quiser, é ato explícito dele na tabela própria da V4_9
 * ({@code tariff_impact_simulations}), que não é base de faturamento.
 *
 * <p>Débitos, créditos e retenção não entram: são da conta individual, não
 * da estrutura tarifária; o impacto tarifário é água + esgoto.
 */
public final class ImpactSimulator {

    /** Esgoto de uma unidade na simulação (mesmos parâmetros da conta). */
    public record SewerInput(int baseVolume, SewerParameters parameters) {
    }

    /**
     * Uma unidade da base real na simulação: as economias por categoria e o
     * consumo faturado do período de referência.
     *
     * @param sewer {@code null} = unidade sem esgoto faturado
     */
    public record SimulationUnit(UUID unitId, List<TariffGroup> groups, int waterConsumption, SewerInput sewer) {
        public SimulationUnit {
            if (unitId == null) {
                throw new IllegalArgumentException("Unidade da simulação sem id");
            }
            if (groups == null || groups.isEmpty()) {
                throw new IllegalArgumentException("Unidade " + unitId + " sem economias na simulação");
            }
            groups = List.copyOf(groups);
            if (waterConsumption < 0) {
                throw new IllegalArgumentException("Consumo negativo na unidade " + unitId + ": " + waterConsumption);
            }
        }
    }

    /** Perfil de consumo da conta-tipo: 1 economia da categoria, sem tarifa social salvo {@code social}. */
    public record TypicalProfile(String label, UUID categoryId, UUID subcategoryId, int consumption, boolean social) {
        public TypicalProfile {
            if (label == null || label.isBlank() || categoryId == null || consumption < 0) {
                throw new IllegalArgumentException("Perfil de conta-tipo inválido: " + label);
            }
        }
    }

    /** Receita por categoria/subcategoria: bruto, desconto social e líquido, vigente × proposta. */
    public record CategoryRevenue(UUID categoryId, UUID subcategoryId, long units, long economies,
                                  BigDecimal currentGross, BigDecimal currentDiscount, BigDecimal currentNet,
                                  BigDecimal proposedGross, BigDecimal proposedDiscount, BigDecimal proposedNet,
                                  BigDecimal deltaNet, BigDecimal deltaPct) {
    }

    /** Receita por faixa (linha da tarifa): a parcela fixa é a faixa {@code MINIMUM}. */
    public record BlockRevenue(UUID categoryId, UUID subcategoryId, ChargeLine.Type lineType,
                               int fromM3, Integer toM3,
                               BigDecimal currentGross, BigDecimal currentDiscount, BigDecimal currentNet,
                               BigDecimal proposedGross, BigDecimal proposedDiscount, BigDecimal proposedNet,
                               BigDecimal deltaNet) {
    }

    /** Conta-tipo: quanto a conta de 1 economia naquele perfil custa em cada estrutura. */
    public record TypicalBill(String label, UUID categoryId, int consumption,
                              BigDecimal currentNet, BigDecimal proposedNet,
                              BigDecimal delta, BigDecimal deltaPct) {
    }

    /** Efeito na tarifa social e no subsídio: o desconto concedido é o subsídio implícito da estrutura. */
    public record SocialImpact(long socialEconomies, BigDecimal currentDiscount, BigDecimal proposedDiscount,
                               BigDecimal discountDelta, BigDecimal currentSocialNet, BigDecimal proposedSocialNet) {
    }

    /** Resultado por unidade (água + esgoto líquidos), para conferência com a conta da P2.5. */
    public record UnitResult(UUID unitId, BigDecimal currentNet, BigDecimal proposedNet, BigDecimal delta) {
    }

    public record ImpactReport(List<UnitResult> units, List<CategoryRevenue> revenueByCategory,
                               List<BlockRevenue> revenueByBlock, List<TypicalBill> typicalBills,
                               SocialImpact socialImpact,
                               BigDecimal currentRevenue, BigDecimal proposedRevenue, BigDecimal revenueDelta) {
        public ImpactReport {
            units = List.copyOf(units);
            revenueByCategory = List.copyOf(revenueByCategory);
            revenueByBlock = List.copyOf(revenueByBlock);
            typicalBills = List.copyOf(typicalBills);
        }
    }

    private ImpactSimulator() {
    }

    public static ImpactReport simulate(TariffVersion current, TariffVersion proposed,
                                        List<SimulationUnit> units, LocalDate periodFrom, LocalDate periodTo,
                                        List<TypicalProfile> profiles) {
        if (current == null || proposed == null) {
            throw new TariffConfigurationException("Simulação exige a estrutura vigente e a proposta");
        }
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("Simulação sem massa: a base de unidades é obrigatória");
        }
        if (periodFrom == null || periodTo == null || periodTo.isBefore(periodFrom)) {
            throw new IllegalArgumentException("Período de referência da simulação inválido");
        }
        TariffVersion currentCalc = redated(current, periodFrom, periodTo);
        TariffVersion proposedCalc = redated(proposed, periodFrom, periodTo);
        TariffSchedule currentSchedule = new TariffSchedule(List.of(currentCalc));
        TariffSchedule proposedSchedule = new TariffSchedule(List.of(proposedCalc));

        Map<String, CategoryAccumulator> byCategory = new LinkedHashMap<>();
        Map<String, BlockAccumulator> byBlock = new LinkedHashMap<>();
        Map<String, long[]> unitsAndEconomiesByCategory = new LinkedHashMap<>();
        for (SimulationUnit unit : units) {
            for (TariffGroup group : unit.groups()) {
                long[] counts = unitsAndEconomiesByCategory.computeIfAbsent(
                        group.categoryId() + "/" + group.subcategoryId(), k -> new long[2]);
                counts[0]++;
                counts[1] += group.economies();
            }
        }
        List<UnitResult> unitResults = new ArrayList<>();
        BigDecimal currentRevenue = Money.ZERO;
        BigDecimal proposedRevenue = Money.ZERO;
        long socialEconomies = 0;
        BigDecimal currentSocialNet = Money.ZERO;
        BigDecimal proposedSocialNet = Money.ZERO;

        for (SimulationUnit unit : units) {
            UnitComputation cur = compute(currentSchedule, unit, periodFrom, periodTo);
            UnitComputation prop = compute(proposedSchedule, unit, periodFrom, periodTo);
            unitResults.add(new UnitResult(unit.unitId(), cur.net(), prop.net(), prop.net().subtract(cur.net())));
            currentRevenue = currentRevenue.add(cur.net());
            proposedRevenue = proposedRevenue.add(prop.net());
            accumulate(byCategory, byBlock, cur, true);
            accumulate(byCategory, byBlock, prop, false);
            long unitSocial = unit.groups().stream().mapToInt(TariffGroup::socialEconomies).sum();
            if (unitSocial > 0) {
                socialEconomies += unitSocial;
                currentSocialNet = currentSocialNet.add(cur.net());
                proposedSocialNet = proposedSocialNet.add(prop.net());
            }
        }

        List<CategoryRevenue> categoryRevenue = new ArrayList<>();
        BigDecimal currentDiscount = Money.ZERO;
        BigDecimal proposedDiscount = Money.ZERO;
        for (Map.Entry<String, CategoryAccumulator> entry : byCategory.entrySet()) {
            CategoryAccumulator acc = entry.getValue();
            long[] counts = unitsAndEconomiesByCategory.getOrDefault(entry.getKey(), new long[2]);
            acc.units = counts[0];
            acc.economies = counts[1];
            categoryRevenue.add(acc.toRevenue());
            currentDiscount = currentDiscount.add(acc.currentDiscount);
            proposedDiscount = proposedDiscount.add(acc.proposedDiscount);
        }
        List<BlockRevenue> blockRevenue = new ArrayList<>();
        for (BlockAccumulator acc : byBlock.values()) {
            blockRevenue.add(acc.toRevenue());
        }

        List<TypicalBill> typicalBills = new ArrayList<>();
        for (TypicalProfile profile : profiles == null ? List.<TypicalProfile>of() : profiles) {
            TariffGroup group = new TariffGroup(profile.categoryId(), profile.subcategoryId(), 1, profile.social() ? 1 : 0);
            BigDecimal curNet = TariffEngine.categoriesFor(currentCalc, List.of(group), profile.consumption())
                    .get(0).net();
            BigDecimal propNet = TariffEngine.categoriesFor(proposedCalc, List.of(group), profile.consumption())
                    .get(0).net();
            typicalBills.add(new TypicalBill(profile.label(), profile.categoryId(), profile.consumption(),
                    curNet, propNet, propNet.subtract(curNet), TariffComparison.deltaPct(curNet, propNet)));
        }

        SocialImpact social = new SocialImpact(socialEconomies, currentDiscount, proposedDiscount,
                proposedDiscount.subtract(currentDiscount), currentSocialNet, proposedSocialNet);
        return new ImpactReport(unitResults, categoryRevenue, blockRevenue, typicalBills, social,
                currentRevenue, proposedRevenue, proposedRevenue.subtract(currentRevenue));
    }

    // ------------------------------------------------------------------------------------------

    private record UnitComputation(BigDecimal net, List<CategoryCharge> categories) {
    }

    private static UnitComputation compute(TariffSchedule schedule, SimulationUnit unit,
                                           LocalDate from, LocalDate to) {
        TariffCharge water = TariffEngine.calculate(schedule, from, to, unit.groups(), unit.waterConsumption());
        BigDecimal net = water.net();
        List<CategoryCharge> categories = new ArrayList<>();
        for (SliceCharge slice : water.slices()) {
            categories.addAll(slice.categories());
        }
        if (unit.sewer() != null) {
            SewerCharge sewer = TariffEngine.calculateSewer(schedule, from, to, unit.groups(),
                    unit.sewer().baseVolume(), unit.sewer().parameters());
            net = net.add(sewer.net());
            for (SliceCharge slice : sewer.charge().slices()) {
                categories.addAll(slice.categories());
            }
        }
        return new UnitComputation(net, categories);
    }

    private static void accumulate(Map<String, CategoryAccumulator> byCategory,
                                   Map<String, BlockAccumulator> byBlock,
                                   UnitComputation computation, boolean current) {
        for (CategoryCharge category : computation.categories()) {
            String catKey = category.categoryId() + "/" + category.subcategoryId();
            CategoryAccumulator cat = byCategory.computeIfAbsent(catKey,
                    k -> new CategoryAccumulator(category.categoryId(), category.subcategoryId()));
            cat.add(category, current);
            for (ChargeLine line : category.lines()) {
                String blockKey = catKey + "/" + line.type() + "/" + line.fromM3() + "/" + line.toM3();
                BlockAccumulator block = byBlock.computeIfAbsent(blockKey,
                        k -> new BlockAccumulator(category.categoryId(), category.subcategoryId(),
                                line.type(), line.fromM3(), line.toM3()));
                block.add(line, current);
            }
        }
    }

    /** Cópia de cálculo com a vigência trocada pelo período de referência (ver javadoc da classe). */
    private static TariffVersion redated(TariffVersion version, LocalDate from, LocalDate to) {
        return new TariffVersion(version.id(), from, to, version.rules(), version.socialRule());
    }

    private static final class CategoryAccumulator {
        private final UUID categoryId;
        private final UUID subcategoryId;
        private long units;
        private long economies;
        private BigDecimal currentGross = Money.ZERO;
        private BigDecimal currentDiscount = Money.ZERO;
        private BigDecimal proposedGross = Money.ZERO;
        private BigDecimal proposedDiscount = Money.ZERO;

        private CategoryAccumulator(UUID categoryId, UUID subcategoryId) {
            this.categoryId = categoryId;
            this.subcategoryId = subcategoryId;
        }

        private void add(CategoryCharge charge, boolean current) {
            if (current) {
                currentGross = currentGross.add(charge.gross());
                currentDiscount = currentDiscount.add(charge.discount());
            } else {
                proposedGross = proposedGross.add(charge.gross());
                proposedDiscount = proposedDiscount.add(charge.discount());
            }
        }

        private CategoryRevenue toRevenue() {
            BigDecimal currentNet = currentGross.subtract(currentDiscount);
            BigDecimal proposedNet = proposedGross.subtract(proposedDiscount);
            return new CategoryRevenue(categoryId, subcategoryId, units, economies,
                    currentGross, currentDiscount, currentNet,
                    proposedGross, proposedDiscount, proposedNet,
                    proposedNet.subtract(currentNet), TariffComparison.deltaPct(currentNet, proposedNet));
        }
    }

    private static final class BlockAccumulator {
        private final UUID categoryId;
        private final UUID subcategoryId;
        private final ChargeLine.Type lineType;
        private final int fromM3;
        private final Integer toM3;
        private BigDecimal currentGross = Money.ZERO;
        private BigDecimal currentDiscount = Money.ZERO;
        private BigDecimal proposedGross = Money.ZERO;
        private BigDecimal proposedDiscount = Money.ZERO;

        private BlockAccumulator(UUID categoryId, UUID subcategoryId, ChargeLine.Type lineType,
                                 int fromM3, Integer toM3) {
            this.categoryId = categoryId;
            this.subcategoryId = subcategoryId;
            this.lineType = lineType;
            this.fromM3 = fromM3;
            this.toM3 = toM3;
        }

        private void add(ChargeLine line, boolean current) {
            if (current) {
                currentGross = currentGross.add(line.gross());
                currentDiscount = currentDiscount.add(line.discount());
            } else {
                proposedGross = proposedGross.add(line.gross());
                proposedDiscount = proposedDiscount.add(line.discount());
            }
        }

        private BlockRevenue toRevenue() {
            BigDecimal currentNet = currentGross.subtract(currentDiscount);
            BigDecimal proposedNet = proposedGross.subtract(proposedDiscount);
            return new BlockRevenue(categoryId, subcategoryId, lineType, fromM3, toM3,
                    currentGross, currentDiscount, currentNet,
                    proposedGross, proposedDiscount, proposedNet,
                    proposedNet.subtract(currentNet));
        }
    }

}
