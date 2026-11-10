package br.com.ziipsan.operations.metering.hydrometry.renewal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste §4.3 da estimativa da II-5: a selecção respeita a restrição e a
 * ordenação por score, sem duplicados. Scores da massa (config de
 * {@link RenewalFixtures}): M1=0.90, M2=0.67, M3=0.375, M5=0.18.
 */
class AnnualRenewalPlanTest {

    private static final RenewalScoreConfig CFG = RenewalFixtures.config();

    private AnnualRenewalPlan.RenewalCandidate m1() {
        return RenewalFixtures.candidate(RenewalFixtures.snapshot("21", "H-001",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2016, 1, 1), 20_000, "TEC_A", LocalDate.of(2018, 6, 1),
                new BigDecimal("600")), CFG);
    }
    private AnnualRenewalPlan.RenewalCandidate m2() {
        return RenewalFixtures.candidate(RenewalFixtures.snapshot("22", "H-002",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", LocalDate.of(2020, 1, 1),
                new BigDecimal("500")), CFG);
    }
    private AnnualRenewalPlan.RenewalCandidate m3() {
        return RenewalFixtures.candidate(RenewalFixtures.snapshot("23", "H-003",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2021, 1, 1), 5_000, "TEC_B", LocalDate.of(2019, 1, 1),
                new BigDecimal("300")), CFG);
    }
    private AnnualRenewalPlan.RenewalCandidate m4Available() {
        return RenewalFixtures.candidate(RenewalFixtures.snapshot("24", "H-004",
                MeterRenewalSnapshot.MeterParkStatus.AVAILABLE,
                LocalDate.of(2021, 1, 1), 5_000, "TEC_B", LocalDate.of(2019, 1, 1),
                new BigDecimal("300")), CFG);
    }
    private AnnualRenewalPlan.RenewalCandidate m5() {
        return RenewalFixtures.candidate(RenewalFixtures.snapshot("25", "H-005",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2024, 1, 1), 0, "TEC_B", null,
                new BigDecimal("100")), CFG);
    }

    @Test @DisplayName("contagem: ByCount(2) selecciona os 2 maiores scores, na ordem, sem duplicados")
    void countRestrictionSelectsTopInOrder() {
        var plan = AnnualRenewalPlan.build(2026, List.of(m3(), m5(), m1(), m4Available(), m2()),
                new AnnualRenewalPlan.RenewalRestriction.ByCount(2), null);
        assertEquals(2, plan.lines().size());
        assertEquals("H-001", plan.lines().get(0).meterNumber());
        assertEquals(1, plan.lines().get(0).position());
        RenewalFixtures.assertBd("0.90", plan.lines().get(0).totalScore());
        assertEquals("H-002", plan.lines().get(1).meterNumber());
        assertEquals(2, plan.lines().get(1).position());
        RenewalFixtures.assertBd("0.67", plan.lines().get(1).totalScore());
        assertEquals(4, plan.eligibleCount());
        assertEquals(1, plan.excludedByStatusCount());
        assertEquals(0, plan.excludedBelowMinScoreCount());
        assertEquals(2, plan.excludedByRestrictionCount());
        Set<UUID> ids = new HashSet<>();
        plan.lines().forEach(l -> assertTrue(ids.add(l.meterId()), "linha duplicada no plano"));
        RenewalFixtures.assertBd("1100", plan.totalEstimatedCost());
    }

    @Test @DisplayName("orçamento 900: M1(600) entra, M2(500) não cabe e é saltado, M3(300) cabe, M5(100) já não")
    void budgetSkipsExpensiveAndContinues() {
        var plan = AnnualRenewalPlan.build(2026, List.of(m1(), m2(), m3(), m5()),
                new AnnualRenewalPlan.RenewalRestriction.ByBudget(new BigDecimal("900")), null);
        assertEquals(List.of("H-001", "H-003"),
                plan.lines().stream().map(AnnualRenewalPlan.PlanLine::meterNumber).toList());
        assertEquals(2, plan.excludedByRestrictionCount());
        RenewalFixtures.assertBd("900", plan.totalEstimatedCost());
    }

    @Test @DisplayName("orçamento sem custo numa linha elegível é erro explícito")
    void budgetRequiresCosts() {
        var noCost = RenewalFixtures.candidate(RenewalFixtures.snapshot("26", "H-006",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2016, 1, 1), 20_000, "TEC_A", null, null), CFG);
        assertThrows(IllegalArgumentException.class, () ->
                AnnualRenewalPlan.build(2026, List.of(noCost),
                        new AnnualRenewalPlan.RenewalRestriction.ByBudget(new BigDecimal("900")), null));
    }

    @Test @DisplayName("piso de score 0.50: só M1 e M2 elegíveis; M3 e M5 contam como abaixo do piso")
    void minScoreFloor() {
        var plan = AnnualRenewalPlan.build(2026, List.of(m1(), m2(), m3(), m5()),
                new AnnualRenewalPlan.RenewalRestriction.ByCount(10), new BigDecimal("0.50"));
        assertEquals(2, plan.lines().size());
        assertEquals(2, plan.eligibleCount());
        assertEquals(2, plan.excludedBelowMinScoreCount());
        assertEquals(0, plan.excludedByRestrictionCount());
    }

    @Test @DisplayName("hidrômetro duplicado na entrada é recusado — o plano nunca tem duplicados")
    void duplicateMeterRejected() {
        var c = m1();
        assertThrows(IllegalArgumentException.class, () ->
                AnnualRenewalPlan.build(2026, List.of(c, c),
                        new AnnualRenewalPlan.RenewalRestriction.ByCount(2), null));
    }

    @Test @DisplayName("desempate do plano: score igual -> vence a próxima verificação mais cedo, antes do número")
    void tieBreakByVerificationDateBeforeNumber() {
        // Mesmos dados de score (0.67); H-900 vence em 2025-01-01, H-100 só em 2027-01-01.
        var early = RenewalFixtures.candidate(RenewalFixtures.snapshot("27", "H-900",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", LocalDate.of(2018, 1, 1),
                new BigDecimal("100")), CFG);
        var late = RenewalFixtures.candidate(RenewalFixtures.snapshot("28", "H-100",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", LocalDate.of(2020, 1, 1),
                new BigDecimal("100")), CFG);
        RenewalFixtures.assertBd("0.67", early.scored().totalScore());
        RenewalFixtures.assertBd("0.67", late.scored().totalScore());
        var plan = AnnualRenewalPlan.build(2026, List.of(late, early),
                new AnnualRenewalPlan.RenewalRestriction.ByCount(2), null);
        assertEquals("H-900", plan.lines().get(0).meterNumber());
        assertEquals(LocalDate.of(2025, 1, 1), plan.lines().get(0).nextVerificationOn());
        assertEquals(MetrologicalClock.ClockStatus.OVERDUE, plan.lines().get(0).clockStatus());
        assertEquals("H-100", plan.lines().get(1).meterNumber());
    }

    @Test @DisplayName("custo total é null quando uma linha seleccionada não tem custo (total desconhecido, não inventado)")
    void totalCostNullWhenSelectedLacksCost() {
        var noCost = RenewalFixtures.candidate(RenewalFixtures.snapshot("29", "H-009",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2016, 1, 1), 20_000, "TEC_A", null, null), CFG);
        var plan = AnnualRenewalPlan.build(2026, List.of(noCost, m2()),
                new AnnualRenewalPlan.RenewalRestriction.ByCount(2), null);
        assertEquals(2, plan.lines().size());
        assertNull(plan.totalEstimatedCost());
    }
}
