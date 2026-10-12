package br.com.sigsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static br.com.sigsan.operations.metering.rules.Fixtures.bd;
import static br.com.sigsan.operations.metering.rules.Fixtures.calc;
import static br.com.sigsan.operations.metering.rules.Fixtures.input;
import static br.com.sigsan.operations.metering.rules.Fixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consumo de ligação sem hidrômetro (relatório 02, 3.9; casos N-01 a N-05). Valores FICTÍCIOS,
 * recalculados à mão. Regra de arredondamento sob teste: área virtual com 2 casas HALF_UP.
 */
class NonMeteredConsumptionTest {

    private static final YearMonth REF = Fixtures.REF;
    private static final YearMonth JAN = YearMonth.of(2026, 1);

    private static List<AreaConsumptionRule> areaTable(YearMonth from, int upTo40, int upTo60, int upTo100) {
        return List.of(new AreaConsumptionRule(from, bd("40"), upTo40),
                new AreaConsumptionRule(from, bd("60"), upTo60),
                new AreaConsumptionRule(from, bd("100"), upTo100));
    }

    private static List<ParameterConsumptionRule> parameterTable() {
        return List.of(new ParameterConsumptionRule(JAN, 1, 6), new ParameterConsumptionRule(JAN, 2, 9),
                new ParameterConsumptionRule(JAN, 3, 12));
    }

    private static EconomyGroup group(int economies, Integer equivalent, List<AreaConsumptionRule> areaRules,
                                      List<ParameterConsumptionRule> parameterRules) {
        return new EconomyGroup("RES", economies, 10, equivalent, null, areaRules, parameterRules);
    }

    private static int compute(NonMeteredMode mode, List<EconomyGroup> groups, NonMeteredFacts facts) {
        return NonMeteredConsumption.compute(mode, groups, facts, REF).consumption();
    }

    // ---- Por tarifa ------------------------------------------------------------------------

    @Test
    @DisplayName("N-01: RES 3 economias, por tarifa, sem fator de economias: 10 x 3 = 30")
    void n01_byTariff() {
        assertThat(compute(NonMeteredMode.BY_TARIFF, List.of(Fixtures.res(3)), NonMeteredFacts.NONE)).isEqualTo(30);
    }

    @Test
    @DisplayName("N-02: idem com fator de economias da categoria = 2 (equivalentes): 10 x 2 = 20")
    void n02_byTariffWithEquivalentEconomies() {
        EconomyGroup g = new EconomyGroup("RES", 3, 10, 2, null, List.of(), List.of());
        assertThat(compute(NonMeteredMode.BY_TARIFF, List.of(g), NonMeteredFacts.NONE)).isEqualTo(20);
    }

    // ---- Por área --------------------------------------------------------------------------

    @Test
    @DisplayName("N-03: área 85,40 m2, 2 economias virtuais: 85,40 / 2 = 42,70 -> faixa até 60 (consumo 10) x 2 economias = 20")
    void n03_byArea() {
        EconomyGroup g = group(2, null, areaTable(JAN, 8, 10, 14), List.of());
        NonMeteredFacts facts = new NonMeteredFacts(bd("85.40"), 2, null, null);
        assertThat(compute(NonMeteredMode.BY_AREA, List.of(g), facts)).isEqualTo(20);
    }

    @ParameterizedTest(name = "área {0} / 2 -> consumo {1}")
    @DisplayName("fronteiras da faixa de área: o teto é inclusivo e a área virtual arredonda HALF_UP a 2 casas")
    @CsvSource({
            // 80,00 / 2 = 40,00 -> faixa até 40 (8) x 2 = 16
            "80.00, 16",
            // 80,02 / 2 = 40,01 -> faixa até 60 (10) x 2 = 20
            "80.02, 20",
            // 80,01 / 2 = 40,005 -> HALF_UP 40,01 -> faixa até 60 = 20 (com DOWN seria 40,00 -> 16)
            "80.01, 20",
            // 120,00 / 2 = 60,00 -> até 60 (10) x 2 = 20
            "120.00, 20",
            // 200,00 / 2 = 100,00 -> até 100 (14) x 2 = 28
            "200.00, 28",
    })
    void areaBoundaries(String area, int expected) {
        EconomyGroup g = group(2, null, areaTable(JAN, 8, 10, 14), List.of());
        assertThat(compute(NonMeteredMode.BY_AREA, List.of(g), new NonMeteredFacts(bd(area), 2, null, null)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("vigência: vale a mais recente que já começou (jun/2026), e a futura (dez/2026) é ignorada")
    void latestStartedVigencyWins() {
        List<AreaConsumptionRule> rules = new java.util.ArrayList<>(areaTable(JAN, 8, 10, 14));
        rules.addAll(areaTable(YearMonth.of(2026, 6), 9, 11, 15));
        rules.addAll(areaTable(YearMonth.of(2026, 12), 99, 99, 99));
        EconomyGroup g = group(2, null, rules, List.of());
        // área virtual 42,70 -> faixa até 60 da vigência de junho: 11 x 2 = 22
        assertThat(compute(NonMeteredMode.BY_AREA, List.of(g), new NonMeteredFacts(bd("85.40"), 2, null, null)))
                .isEqualTo(22);
    }

    @Test
    @DisplayName("por área usa as economias equivalentes (10 x 3), como o relatório descreve")
    void areaUsesEquivalentEconomies() {
        EconomyGroup g = group(2, 3, areaTable(JAN, 8, 10, 14), List.of());
        assertThat(compute(NonMeteredMode.BY_AREA, List.of(g), new NonMeteredFacts(bd("85.40"), 2, null, null)))
                .isEqualTo(30);
    }

    @Test
    @DisplayName("área ausente, economias virtuais ausentes ou área fora de toda faixa são recusadas")
    void areaErrors() {
        EconomyGroup g = group(2, null, areaTable(JAN, 8, 10, 14), List.of());
        assertThatThrownBy(() -> compute(NonMeteredMode.BY_AREA, List.of(g), NonMeteredFacts.NONE))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.MISSING_BUILT_AREA));
        assertThatThrownBy(() -> compute(NonMeteredMode.BY_AREA, List.of(g),
                new NonMeteredFacts(bd("85.40"), null, null, null)))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.MISSING_VIRTUAL_ECONOMIES));
        assertThatThrownBy(() -> compute(NonMeteredMode.BY_AREA, List.of(g),
                new NonMeteredFacts(bd("300"), 2, null, null)))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.NO_CONSUMPTION_RULE_FOR_VALUE));
        // categoria sem tabela nenhuma
        assertThatThrownBy(() -> compute(NonMeteredMode.BY_AREA, List.of(group(2, null, List.of(), List.of())),
                new NonMeteredFacts(bd("85.40"), 2, null, null)))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.NO_CONSUMPTION_RULE_FOR_VALUE));
    }

    // ---- Por moradores e pontos --------------------------------------------------------------

    @Test
    @DisplayName("N-04: por moradores, imóvel de 1 economia sem número de moradores: assume 1 (consumo 6) e AVISA")
    void n04_residentsMissingAssumedOneWithWarning() {
        NonMeteredConsumption.Result r = NonMeteredConsumption.compute(NonMeteredMode.BY_RESIDENTS,
                List.of(group(1, null, List.of(), parameterTable())), NonMeteredFacts.NONE, REF);
        assertThat(r.consumption()).isEqualTo(6);
        assertThat(r.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.RESIDENTS_MISSING_ASSUMED_ONE);
    }

    @ParameterizedTest(name = "{0} moradores -> consumo {1}")
    @DisplayName("por moradores: a linha de menor teto que cobre o valor (tetos 1, 2 e 3)")
    @CsvSource({"0, 6", "1, 6", "2, 9", "3, 12"})
    void residentsLookup(int residents, int expected) {
        NonMeteredConsumption.Result r = NonMeteredConsumption.compute(NonMeteredMode.BY_RESIDENTS,
                List.of(group(1, null, List.of(), parameterTable())), new NonMeteredFacts(null, null, null, residents),
                REF);
        assertThat(r.consumption()).isEqualTo(expected);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("moradores acima da maior faixa: recusado; por parâmetro multiplica as economias reais (2 x 9 = 18)")
    void residentsAboveTableAndEconomyMultiplication() {
        EconomyGroup g = group(2, 5, List.of(), parameterTable());
        assertThatThrownBy(() -> compute(NonMeteredMode.BY_RESIDENTS, List.of(g),
                new NonMeteredFacts(null, null, null, 4)))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.NO_CONSUMPTION_RULE_FOR_VALUE));
        assertThat(compute(NonMeteredMode.BY_RESIDENTS, List.of(g), new NonMeteredFacts(null, null, null, 2)))
                .isEqualTo(18);
    }

    @Test
    @DisplayName("por pontos de utilização: ausente assume 1 com aviso próprio; informado 3 dá 12")
    void utilizationPoints() {
        EconomyGroup g = group(1, null, List.of(), parameterTable());
        NonMeteredConsumption.Result missing = NonMeteredConsumption.compute(NonMeteredMode.BY_UTILIZATION_POINTS,
                List.of(g), NonMeteredFacts.NONE, REF);
        assertThat(missing.consumption()).isEqualTo(6);
        assertThat(missing.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.UTILIZATION_POINTS_MISSING_ASSUMED_ONE);
        assertThat(compute(NonMeteredMode.BY_UTILIZATION_POINTS, List.of(g), new NonMeteredFacts(null, null, 3, null)))
                .isEqualTo(12);
    }

    // ---- Dentro da cascata (ligação sem hidrômetro) -------------------------------------------

    @Test
    @DisplayName("N-01 pela cascata: sem hidrômetro, tipo NÃO MEDIDO, 30 m3, sem leitura faturada nem aviso de média")
    void n01_throughTheCalculator() {
        ConsumptionResult r = calc(policy(NonMeteredMode.BY_TARIFF), input(null, List.of(Fixtures.res(3)), List.of()));
        assertThat(r.billedConsumption()).isEqualTo(30);
        assertThat(r.type()).isEqualTo(ConsumptionType.NOT_METERED);
        assertThat(r.anomaly()).isNull();
        assertThat(r.billedReading()).isNull();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("o ajuste de múltiplas economias NÃO vale para não medido: N-02 (3 economias, equivalentes 2) fatura 20, não 18")
    void multiEconomyAdjustmentDoesNotApplyToNotMetered() {
        EconomyGroup g = new EconomyGroup("RES", 3, 10, 2, null, List.of(), List.of());
        ConsumptionResult r = calc(policy(NonMeteredMode.BY_TARIFF), input(null, List.of(g), List.of()));
        assertThat(r.billedConsumption()).isEqualTo(20);
        assertThat(r.discardedRemainder()).isZero();
    }

    @Test
    @DisplayName("N-03 pela cascata, modo por área")
    void n03_throughTheCalculator() {
        EconomyGroup g = group(2, null, areaTable(JAN, 8, 10, 14), List.of());
        ConsumptionResult r = calc(policy(NonMeteredMode.BY_AREA), input(null, List.of(g), List.of())
                .facts(new NonMeteredFacts(bd("85.40"), 2, null, null)));
        assertThat(r.billedConsumption()).isEqualTo(20);
        assertThat(r.type()).isEqualTo(ConsumptionType.NOT_METERED);
    }

    @Test
    @DisplayName("consumo mínimo fixado na ligação sem hidrômetro vale direto (15), tipo MÍNIMO FIXADO, sem comparar com a tarifa")
    void fixedMinimumOnNotMetered() {
        ConsumptionResult r = calc(policy(), input(null, List.of(Fixtures.res(1)), List.of()).fixedMinimum(15));
        assertThat(r.billedConsumption()).isEqualTo(15);
        assertThat(r.type()).isEqualTo(ConsumptionType.MINIMUM_FIXED);
        // mesmo quando o fixado (4) é menor que o mínimo da tarifa (10)
        assertThat(calc(policy(), input(null, List.of(Fixtures.res(1)), List.of()).fixedMinimum(4))
                .billedConsumption()).isEqualTo(4);
    }

    @Test
    @DisplayName("situação especial com consumo fixo para não medido, e permissão de faturar por tipo")
    void specialSituationAndPermission() {
        SpecialSituation special = new SpecialSituation(YearMonth.of(2026, 1), null, false, 99, 12, null);
        ConsumptionResult r = calc(policy(), input(null, List.of(Fixtures.res(1)), List.of()).special(special));
        assertThat(r.billedConsumption()).isEqualTo(12);
        assertThat(r.type()).isEqualTo(ConsumptionType.SPECIAL_SITUATION_FIXED);

        ConsumptionResult notBillable = calc(policy(), input(null, List.of(Fixtures.res(1)), List.of())
                .billable(ConsumptionType.REAL));
        assertThat(notBillable.billedConsumption()).isZero();
        assertThat(notBillable.calculatedConsumption()).isEqualTo(10);
        assertThat(notBillable.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.CONSUMPTION_TYPE_NOT_BILLABLE);
    }

    @Test
    @DisplayName("tabelas e dados inválidos são recusados na construção")
    void validation() {
        assertThatThrownBy(() -> new AreaConsumptionRule(JAN, BigDecimal.ZERO, 1))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new ParameterConsumptionRule(JAN, -1, 1)).isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new EconomyGroup("RES", 0, 10, null, null, null, null))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.NO_ECONOMIES));
    }
}
