package br.com.sigsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static br.com.sigsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos T-* do relatório 02 (seção 10.1), com a tarifa FICTÍCIA T1. Cada valor esperado foi
 * recalculado à mão, de forma independente do relatório, antes de virar teste (o passo a passo
 * está no {@code @DisplayName} ou num comentário).
 *
 * <p>T1: RES mín 10 m³ / 30,00, faixas 20→4,00, 50→6,00, aberta→9,00; COM mín 10 m³ / 50,00, faixas
 * 20→7,00, 50→10,00, aberta→14,00.
 */
class TariffEngineTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 4);

    record Case(String id, List<EconomyGroup> groups, int consumption, String expectedValue,
                List<Integer> expectedConsumptionPerCategory) {
        @Override
        public String toString() {
            return id;
        }
    }

    static Stream<Case> tariffCases() {
        return Stream.of(
                // 8 m³ <= mínimo: só a tarifa mínima; consumo da categoria = 8
                new Case("T-01", List.of(res(1)), 8, "30.00", List.of(8)),
                // 10 m³ = mínimo: 30,00; consumo 10
                new Case("T-02", List.of(res(1)), 10, "30.00", List.of(10)),
                // 27: 30 + 10×4 (11..20) + 7×6 (21..27) = 30+40+42
                new Case("T-03", List.of(res(1)), 27, "112.00", List.of(27)),
                // 60: 30 + 10×4 + 30×6 (21..50) + 10×9 (51..60) = 30+40+180+90
                new Case("T-04", List.of(res(1)), 60, "340.00", List.of(60)),
                // 2 eco, 35: mín imóvel 20, excesso 15, 15 DIV 2 = 7; cada eco 30+7×4 = 58; ×2 = 116; consumo (10+7)×2
                new Case("T-05", List.of(res(2)), 35, "116.00", List.of(34)),
                // RES1+COM1, 25: mín 20, excesso 5, 5 DIV 2 = 2; RES 30+2×4 = 38; COM 50+2×7 = 64; 102; consumo 12+12
                new Case("T-06", List.of(res(1), com(1)), 25, "102.00", List.of(12, 12)),
                // 3 eco, 28: mín 30, excesso -2; 28 DIV 3 = 9 < 10: cada eco paga a mínima: 3×30; consumo 9×3
                new Case("T-07", List.of(res(3)), 28, "90.00", List.of(27))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tariffCases")
    @DisplayName("faixas progressivas, consumo mínimo e múltiplas economias (T-01..T-07)")
    void blocksWithMinimum(Case c) {
        List<CategoryCharge> result = TariffEngine.categoriesFor(t1(), c.groups(), c.consumption());

        BigDecimal total = result.stream().map(CategoryCharge::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(c.expectedValue());
        assertThat(result.stream().map(CategoryCharge::consumption).toList()).isEqualTo(c.expectedConsumptionPerCategory());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tariffCases")
    @DisplayName("invariantes (10.16): Σ linhas = categoria; categoria líquida = bruta − desconto; sem desconto sem tarifa social")
    void invariants(Case c) {
        for (CategoryCharge cat : TariffEngine.categoriesFor(t1(), c.groups(), c.consumption())) {
            BigDecimal sumLines = cat.lines().stream().map(ChargeLine::net).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(cat.net()).isEqualByComparingTo(sumLines);
            assertThat(cat.discount()).isEqualByComparingTo("0.00");
            assertThat(cat.lines().get(0).type()).isEqualTo(ChargeLine.Type.MINIMUM);
        }
    }

    @Test
    @DisplayName("T-10: com mínimos diferentes por categoria, excesso ≤ 0 não impede cobrar faixa de quem passou do próprio mínimo")
    void minimumsDifferByCategory() {
        // RES mín 10 (30,00; 20→4,00...), COM mín 20 (50,00; 30→7,00...). RES1 + COM1, C = 25.
        // mín imóvel = 10 + 20 = 30; excesso = -5 ≤ 0; 25 DIV 2 = 12 por economia.
        // RES: usa 10 da fixa e (12 − 10) = 2 na faixa 1: 30 + 2×4 = 38; consumo 12.
        // COM: usa 12 da fixa (< 20): 50,00; excedente −8, nenhuma faixa; consumo 12. Total 88,00.
        TariffVersion v = new TariffVersion(id(101), T1_FROM, null, List.of(
                t1Res(),
                com(20, "50.00", block(30, "7.00"), block(null, "10.00"))), null);

        List<CategoryCharge> r = TariffEngine.categoriesFor(v, List.of(res(1), com(1)), 25);

        assertThat(r.get(0).net()).isEqualByComparingTo("38.00");
        assertThat(r.get(1).net()).isEqualByComparingTo("50.00");
        assertThat(r.stream().map(CategoryCharge::consumption).toList()).containsExactly(12, 12);
    }

    @Test
    @DisplayName("faixa inteira abaixo do consumo mínimo é ignorada")
    void blockBelowMinimumIsSkipped() {
        // RES mín 10; faixas 5→3,00 (abaixo do mínimo), 20→4,00, aberta→9,00. C=27: excedente 17.
        // faixa 5: ignorada; faixa 20: min(17, 20−10) = 10 → 40,00; aberta: 7 × 9 = 63,00. 30+40+63 = 133,00
        TariffVersion v = new TariffVersion(id(102), T1_FROM, null,
                List.of(res(10, "30.00", block(5, "3.00"), block(20, "4.00"), block(null, "9.00"))), null);

        CategoryCharge c = TariffEngine.categoriesFor(v, List.of(res(1)), 27).get(0);

        assertThat(c.net()).isEqualByComparingTo("133.00");
        assertThat(c.consumption()).isEqualTo(27);
    }

    @Test
    @DisplayName("tarifa básica sem franquia (mínimo 0): todo m³ vai para as faixas")
    void basicTariffWithoutFranchise() {
        // RES mín 0, parcela fixa 20,00; faixas 10→2,50, 30→4,00, aberta→8,00.
        // 1 economia, C=14: fixa 20,00 + 10×2,50 = 25,00 + 4×4,00 = 16,00 = 61,00
        // 2 economias, C=14 (7 por economia): por eco 20 + 7×2,50 = 37,50; ×2 = 75,00
        TariffVersion v = new TariffVersion(id(103), T1_FROM, null,
                List.of(res(0, "20.00", block(10, "2.50"), block(30, "4.00"), block(null, "8.00"))), null);

        CategoryCharge one = TariffEngine.categoriesFor(v, List.of(res(1)), 14).get(0);
        CategoryCharge two = TariffEngine.categoriesFor(v, List.of(res(2)), 14).get(0);

        assertThat(one.net()).isEqualByComparingTo("61.00");
        assertThat(one.consumption()).isEqualTo(14);
        assertThat(two.net()).isEqualByComparingTo("75.00");
        assertThat(two.consumption()).isEqualTo(14);
        assertThat(one.lines().get(0).consumption()).as("a parcela fixa não cobre nenhum m³").isZero();
    }

    @Test
    @DisplayName("preço com mais de 2 casas: cada linha é arredondada HALF_UP e a categoria soma as linhas")
    void pricesWithMoreThanTwoDecimals() {
        // mínima 30,1234 → 30,12; 3 m³ × 4,4550 = 13,3650 → 13,37 (HALF_UP). Total 43,49.
        TariffVersion v = new TariffVersion(id(104), T1_FROM, null,
                List.of(res(10, "30.1234", block(null, "4.4550"))), null);

        CategoryCharge c = TariffEngine.categoriesFor(v, List.of(res(1)), 13).get(0);

        assertThat(c.lines().get(0).gross()).isEqualByComparingTo("30.12");
        assertThat(c.lines().get(1).gross()).isEqualByComparingTo("13.37");
        assertThat(c.net()).isEqualByComparingTo("43.49");
    }

    @Test
    @DisplayName("subcategoria usa a regra própria; sem regra própria cai na da categoria")
    void subcategoryFallsBackToCategory() {
        CategoryRule r1 = new CategoryRule(RES, SUB_R1, 8, d("25.00"), null, List.of(block(null, "3.00")));
        TariffVersion v = new TariffVersion(id(105), T1_FROM, null, List.of(t1Res(), r1), null);

        // com subcategoria R1: mín 8, 25,00; C=10: 25 + 2×3 = 31,00
        CategoryCharge withSub = TariffEngine.categoriesFor(v, List.of(new EconomyGroup(RES, SUB_R1, 1, 0)), 10).get(0);
        // outra subcategoria sem regra: cai na da categoria (mín 10, 30,00): C=10 → 30,00
        CategoryCharge other = TariffEngine.categoriesFor(v, List.of(new EconomyGroup(RES, id(12), 1, 0)), 10).get(0);

        assertThat(withSub.net()).isEqualByComparingTo("31.00");
        assertThat(other.net()).isEqualByComparingTo("30.00");
    }

    // ------------------------------------------------------------------------------------------
    // Ausente = erro, nunca valor padrão
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("categoria sem regra na tarifa é erro explícito")
    void missingRuleIsAnError() {
        assertThatThrownBy(() -> TariffEngine.categoriesFor(t1(), List.of(EconomyGroup.of(id(99), 1)), 10))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("Sem regra de tarifa");
    }

    @Test
    @DisplayName("faixas que não cobrem o consumo (última faixa fechada) é erro, não consumo cobrado de menos")
    void blocksThatDoNotCoverConsumptionAreAnError() {
        TariffVersion v = new TariffVersion(id(106), T1_FROM, null,
                List.of(res(10, "30.00", block(20, "4.00"))), null);

        assertThatThrownBy(() -> TariffEngine.categoriesFor(v, List.of(res(1)), 27))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("não cobrem o consumo");
        // dentro da cobertura funciona
        assertThat(TariffEngine.categoriesFor(v, List.of(res(1)), 20).get(0).net()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("entradas inválidas: consumo negativo e unidade sem economias")
    void invalidInputs() {
        assertThatThrownBy(() -> TariffEngine.categoriesFor(t1(), List.of(res(1)), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TariffEngine.categoriesFor(t1(), List.of(), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EconomyGroup.of(RES, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("regras malformadas são recusadas na construção")
    void malformedRulesAreRejected() {
        assertThatThrownBy(() -> res(10, "30.00", block(20, "4.00"), block(null, "9.00"), block(50, "6.00")))
                .as("faixa aberta só pode ser a última").isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> res(10, "30.00", block(30, "4.00"), block(20, "6.00")))
                .as("faixas crescentes").isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> res(10, "-1.00"))
                .as("tarifa mínima negativa").isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> new TariffVersion(id(1), T1_FROM, null, List.of(t1Res(), t1Res()), null))
                .as("regra duplicada").isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> new TariffVersion(id(1), T1_FROM, T1_FROM.minusDays(1), List.of(t1Res()), null))
                .as("fim antes do início").isInstanceOf(TariffConfigurationException.class);
    }

    // ------------------------------------------------------------------------------------------
    // Consumo mínimo da ligação (UC0105) e não medido por tarifa (N-01, N-02)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("N-01: sem hidrômetro, por tarifa: consumo = mín × economias = 10 × 3 = 30, valor 3 × 30,00 = 90,00")
    void nonMeteredByTariff() {
        List<EconomyGroup> groups = List.of(res(3));

        int consumption = TariffEngine.minimumConsumption(t1(), groups);

        assertThat(consumption).isEqualTo(30);
        assertThat(TariffEngine.categoriesFor(t1(), groups, consumption).get(0).net()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("N-02: o fator de economias substitui o número de economias: 10 × 2 = 20")
    void minimumConsumptionUsesEconomyFactor() {
        CategoryRule withFactor = CategoryRule.forCategory(RES, 10, d("30.00"), d("2"),
                List.of(block(null, "4.00")));
        TariffVersion v = new TariffVersion(id(107), T1_FROM, null, List.of(withFactor), null);

        assertThat(TariffEngine.minimumConsumption(v, List.of(res(3)))).isEqualTo(20);
        assertThat(TariffEngine.minimumConsumption(t1(), List.of(res(1), com(1)))).as("RES 10 + COM 10").isEqualTo(20);
    }

    // ------------------------------------------------------------------------------------------
    // Duas vigências no período (4.4)
    // ------------------------------------------------------------------------------------------

    private static TariffVersion versionB(LocalDate from, LocalDate to) {
        // RES mín 10, 33,00; faixas 20→4,40, 50→6,60, aberta→9,90 (FICTÍCIA)
        return new TariffVersion(id(200), from, to,
                List.of(res(10, "33.00", block(20, "4.40"), block(50, "6.60"), block(null, "9.90"))), null);
    }

    private static TariffVersion versionA(LocalDate to) {
        return new TariffVersion(id(100), T1_FROM, to, List.of(t1Res(), t1Com()), null);
    }

    @Test
    @DisplayName("T-08: troca de vigência em 20/09 num período de 05/09 a 04/10 (30 dias): 15/30 e 15/30 → 117,60")
    void twoVersionsEvenSplit() {
        // diasLeitura = (04/10 − 05/09) + 1 = 29 + 1 = 30. A: 05/09..19/09 = 15 dias; B: 20/09..04/10 = 11 + 4 = 15 dias.
        // fator 15/30 = 0,5000 nos dois. A (T1, C=27) = 112,00 → 56,00.
        // B = 33,00 + 10×4,40 + 7×6,60 = 33 + 44 + 46,20 = 123,20 → 61,60. Total 117,60.
        TariffSchedule schedule = scheduleOf(versionA(LocalDate.of(2026, 9, 19)), versionB(LocalDate.of(2026, 9, 20), null));

        TariffCharge charge = TariffEngine.calculate(schedule, FROM, TO, List.of(res(1)), 27);

        assertThat(charge.totalDays()).isEqualTo(30);
        assertThat(charge.slices()).hasSize(2);
        assertThat(charge.slices().get(0).days()).isEqualTo(15);
        assertThat(charge.slices().get(1).days()).isEqualTo(15);
        assertThat(charge.slices().get(0).factor()).isEqualByComparingTo("0.5000");
        assertThat(charge.slices().get(1).factor()).isEqualByComparingTo("0.5000");
        assertThat(charge.slices().get(0).net()).isEqualByComparingTo("56.00");
        assertThat(charge.slices().get(1).net()).isEqualByComparingTo("61.60");
        assertThat(charge.net()).isEqualByComparingTo("117.60");
    }

    @Test
    @DisplayName("T-09: troca em 01/10: A = 26 dias (0,8667), B = 4 dias (0,1333) → 97,07 + 16,42 = 113,49")
    void twoVersionsUnevenSplit() {
        // A: 05/09..30/09 = 26 dias; B: 01/10..04/10 = 4 dias; total 30. Fatores 26/30 = 0,86666→0,8667 e 1 − 0,8667 = 0,1333.
        // A: 112,00 × 0,8667 = 97,0704 → 97,07.   B: 123,20 × 0,1333 = 16,42256 → 16,42.   Soma 113,49.
        // (o valor exato 112×26/30 + 123,2×4/30 = 97,0667 + 16,4267 = 113,4933 também dá 113,49; arredondar
        // cada parcela com a proporção exata daria 97,07 + 16,43 = 113,50, e é isso que o fator de 4 casas evita)
        TariffSchedule schedule = scheduleOf(versionA(LocalDate.of(2026, 9, 30)), versionB(LocalDate.of(2026, 10, 1), null));

        TariffCharge charge = TariffEngine.calculate(schedule, FROM, TO, List.of(res(1)), 27);

        assertThat(charge.slices().get(0).days()).isEqualTo(26);
        assertThat(charge.slices().get(1).days()).isEqualTo(4);
        assertThat(charge.slices().get(0).factor()).isEqualByComparingTo("0.8667");
        assertThat(charge.slices().get(1).factor()).isEqualByComparingTo("0.1333");
        assertThat(charge.slices().get(0).net()).isEqualByComparingTo("97.07");
        assertThat(charge.slices().get(1).net()).isEqualByComparingTo("16.42");
        assertThat(charge.net()).isEqualByComparingTo("113.49");

        // as linhas da categoria somam o valor da categoria (maior resto):
        // A: pesos 30,00/40,00/42,00 sobre 97,07 → 26,00 / 34,67 / 36,40
        // B: pesos 33,00/44,00/46,20 sobre 16,42 → 4,40 / 5,86 / 6,16
        List<ChargeLine> linesA = charge.slices().get(0).categories().get(0).lines();
        List<ChargeLine> linesB = charge.slices().get(1).categories().get(0).lines();
        assertThat(linesA.stream().map(ChargeLine::gross).toList())
                .usingElementComparator(BigDecimal::compareTo).containsExactly(d("26.00"), d("34.67"), d("36.40"));
        assertThat(linesB.stream().map(ChargeLine::gross).toList())
                .usingElementComparator(BigDecimal::compareTo).containsExactly(d("4.40"), d("5.86"), d("6.16"));
    }

    @Test
    @DisplayName("três vigências de 10 dias cada: os fatores fecham em 1,0000 (0,3333 + 0,3333 + 0,3334) e 100,00 não vira 99,99")
    void threeVersionsFactorsCloseAtOne() {
        // período 05/09..04/10 = 30 dias. V1 05..14/09, V2 15..24/09, V3 25/09..04/10; cada uma 10 dias.
        // Tarifa: mínima 100,00 (consumo 5 ≤ mínimo). 100 × 0,3333 = 33,33; 100 × 0,3333 = 33,33; 100 × 0,3334 = 33,34 → 100,00.
        // Com fatores independentes de 4 casas (0,3333 × 3 = 0,9999) o total seria 99,99.
        TariffVersion v1 = new TariffVersion(id(301), T1_FROM, LocalDate.of(2026, 9, 14),
                List.of(res(10, "100.00", block(null, "5.00"))), null);
        TariffVersion v2 = new TariffVersion(id(302), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 24),
                List.of(res(10, "100.00", block(null, "5.00"))), null);
        TariffVersion v3 = new TariffVersion(id(303), LocalDate.of(2026, 9, 25), null,
                List.of(res(10, "100.00", block(null, "5.00"))), null);

        TariffCharge charge = TariffEngine.calculate(scheduleOf(v1, v2, v3), FROM, TO, List.of(res(1)), 5);

        assertThat(charge.slices().stream().map(SliceCharge::factor).toList())
                .usingElementComparator(BigDecimal::compareTo).containsExactly(d("0.3333"), d("0.3333"), d("0.3334"));
        assertThat(charge.slices().stream().map(SliceCharge::factor).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1.0000");
        assertThat(charge.net()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("período dentro de uma única versão: sem rateio (fator 1,0000) mesmo havendo versão futura")
    void periodInsideOneVersionIsNotProrated() {
        TariffSchedule schedule = scheduleOf(versionA(LocalDate.of(2026, 12, 31)), versionB(LocalDate.of(2027, 1, 1), null));

        TariffCharge charge = TariffEngine.calculate(schedule, FROM, TO, List.of(res(1)), 27);

        assertThat(charge.slices()).hasSize(1);
        assertThat(charge.slices().get(0).factor()).isEqualByComparingTo("1.0000");
        assertThat(charge.net()).isEqualByComparingTo("112.00");
    }

    @Test
    @DisplayName("troca de vigência no próprio dia da leitura anterior: vale a nova versão (não há rateio)")
    void versionStartingOnPreviousReadingDay() {
        TariffSchedule schedule = scheduleOf(versionA(LocalDate.of(2026, 9, 4)), versionB(FROM, null));

        TariffCharge charge = TariffEngine.calculate(schedule, FROM, TO, List.of(res(1)), 27);

        assertThat(charge.slices()).hasSize(1);
        assertThat(charge.net()).isEqualByComparingTo("123.20");
    }

    @Test
    @DisplayName("buraco entre vigências, ou período fora de qualquer vigência, é erro")
    void gapsAreErrors() {
        TariffSchedule withGap = scheduleOf(versionA(LocalDate.of(2026, 9, 19)), versionB(LocalDate.of(2026, 9, 25), null));
        assertThatThrownBy(() -> TariffEngine.calculate(withGap, FROM, TO, List.of(res(1)), 27))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("2026-09-20");

        assertThatThrownBy(() -> TariffEngine.calculate(t1Schedule(), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 30),
                List.of(res(1)), 10))
                .isInstanceOf(TariffConfigurationException.class);

        TariffSchedule closed = scheduleOf(versionA(LocalDate.of(2026, 9, 20)));
        assertThatThrownBy(() -> TariffEngine.calculate(closed, FROM, TO, List.of(res(1)), 10))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("2026-09-21");
    }

    @Test
    @DisplayName("vigências que se sobrepõem são recusadas (vigência aberta + nova versão; mesmo dia)")
    void overlappingVersionsAreRejected() {
        assertThatThrownBy(() -> scheduleOf(versionA(null), versionB(LocalDate.of(2026, 9, 20), null)))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("sobrepõem");
        assertThatThrownBy(() -> scheduleOf(versionA(LocalDate.of(2026, 9, 20)), versionB(LocalDate.of(2026, 9, 20), null)))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> TariffEngine.calculate(t1Schedule(), TO, FROM, List.of(res(1)), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T-09 com RES+COM e 2 economias: o total do imóvel é a soma das categorias de cada fatia")
    void prorationWithSeveralCategories() {
        // Versão A: T1 (RES+COM). Versão B: mesma estrutura com preços fictícios maiores.
        TariffVersion a = versionA(LocalDate.of(2026, 9, 19));
        TariffVersion b = new TariffVersion(id(201), LocalDate.of(2026, 9, 20), null,
                List.of(res(10, "33.00", block(20, "4.40"), block(50, "6.60"), block(null, "9.90")),
                        com(10, "55.00", block(20, "7.70"), block(50, "11.00"), block(null, "15.40"))), null);

        // C=25 (RES1+COM1): A = 38 + 64 = 102,00; B: RES 33 + 2×4,40 = 41,80; COM 55 + 2×7,70 = 70,40 → 112,20.
        // 15/30 cada: 102,00 × 0,5 = 51,00; 112,20 × 0,5 = 56,10 (por categoria: 19,00 + 32,00; 20,90 + 35,20). Total 107,10.
        TariffCharge charge = TariffEngine.calculate(scheduleOf(a, b), FROM, TO, List.of(res(1), com(1)), 25);

        assertThat(charge.slices().get(0).net()).isEqualByComparingTo("51.00");
        assertThat(charge.slices().get(1).net()).isEqualByComparingTo("56.10");
        assertThat(charge.net()).isEqualByComparingTo("107.10");
        assertThat(charge.slices().get(1).categories().get(0).net()).isEqualByComparingTo("20.90");
        assertThat(charge.slices().get(1).categories().get(1).net()).isEqualByComparingTo("35.20");
    }
}
