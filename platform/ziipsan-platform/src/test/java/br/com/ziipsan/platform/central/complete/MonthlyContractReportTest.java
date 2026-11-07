package br.com.ziipsan.platform.central.complete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.15: casos do relatório mensal calculados À MÃO (ADR-0025), sob a
 * semântica ÚNICA da P2.15 (correção B1 — ver RELATORIO-P3.15.md):
 * aderência = noPrazo/(noPrazo+estouros) — só resolvidos COM prazo;
 * multa = estouros × mensalidade × penalty_pct/100, por chamado.
 * Mês de 30 dias (2026-11) = 43.200 min; orçamento de erro de 99,9% =
 * 43,2 min. Multa: mensalidade de 1.000.000 de centavos (R$ 10.000,00).
 */
class MonthlyContractReportTest {

    private static final UUID CONTRACT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11); // 30 dias

    private static List<MonthlyContractReport.SeverityTarget> targets() {
        return List.of(
                new MonthlyContractReport.SeverityTarget("S1", new BigDecimal("99"), new BigDecimal("10")),
                new MonthlyContractReport.SeverityTarget("S2", new BigDecimal("98"), new BigDecimal("5")),
                new MonthlyContractReport.SeverityTarget("S3", new BigDecimal("95"), new BigDecimal("2")),
                new MonthlyContractReport.SeverityTarget("S4", new BigDecimal("95"), new BigDecimal("1")),
                new MonthlyContractReport.SeverityTarget("S5", new BigDecimal("90"), new BigDecimal("1")));
    }

    /**
     * Desfecho-base: S1 = 19 no prazo + 1 estouro (denominador 20);
     * S2 = 49 no prazo + 1 estouro (denominador 50); S3 = nada com prazo;
     * S4 = 20 no prazo, sem estouro; S5 = 10 no prazo, sem estouro.
     */
    private static List<MonthlyContractReport.SeverityOutcome> outcomes(long s1Within, long s1Breached) {
        return List.of(
                new MonthlyContractReport.SeverityOutcome("S1", s1Within, s1Breached, 0),
                new MonthlyContractReport.SeverityOutcome("S2", 49, 1, 0),
                new MonthlyContractReport.SeverityOutcome("S3", 0, 0, 0),
                new MonthlyContractReport.SeverityOutcome("S4", 20, 0, 0),
                new MonthlyContractReport.SeverityOutcome("S5", 10, 0, 0));
    }

    private MonthlyContractReport.Report generate(long downtime, LocalDate launch,
                                                  List<MonthlyContractReport.SeverityOutcome> outcomes) {
        return new MonthlyContractReport().generate(CONTRACT, TENANT, NOV_2026, "PUBLIC_SECTOR",
                launch, downtime, 0, 1_000_000, targets(), outcomes);
    }

    private MonthlyContractReport.Report generate(long downtime, long plannedMaintenance, LocalDate launch,
                                                  List<MonthlyContractReport.SeverityOutcome> outcomes) {
        return new MonthlyContractReport().generate(CONTRACT, TENANT, NOV_2026, "PUBLIC_SECTOR",
                launch, downtime, plannedMaintenance, 1_000_000, targets(), outcomes);
    }

    @Test
    @DisplayName("disponibilidade à mão: 60 min em 43.200 → 99,8611%, abaixo da meta de 99,9% do Setor Público")
    void availabilityBelowTargetHandCalculated() {
        MonthlyContractReport.Report r = generate(60, LocalDate.of(2026, 1, 1), outcomes(19, 1));
        assertThat(r.measuredMinutes()).isEqualTo(43_200);
        assertThat(r.availabilityPct()).isEqualByComparingTo("99.8611");
        assertThat(r.availabilityTargetMet()).contains(false);
    }

    @Test
    @DisplayName("fronteira à mão: 43 min → 99,9005% (meta atingida); 44 min → 99,8981% (não atingida); orçamento é 43,2 min")
    void availabilityBoundaryHandCalculated() {
        assertThat(generate(43, LocalDate.of(2026, 1, 1), outcomes(19, 1)).availabilityPct())
                .isEqualByComparingTo("99.9005");
        assertThat(generate(43, LocalDate.of(2026, 1, 1), outcomes(19, 1)).availabilityTargetMet()).contains(true);
        assertThat(generate(44, LocalDate.of(2026, 1, 1), outcomes(19, 1)).availabilityPct())
                .isEqualByComparingTo("99.8981");
        assertThat(generate(44, LocalDate.of(2026, 1, 1), outcomes(19, 1)).availabilityTargetMet()).contains(false);
    }

    @Test
    @DisplayName("M4 — manutenção planejada FORA do denominador: 43.200 − 600 planejados = 42.600; 44 não planejados → 42.556/42.600 = 99,8967%")
    void plannedMaintenanceExcludedFromDenominator() {
        MonthlyContractReport.Report r = generate(44, 600, LocalDate.of(2026, 1, 1), outcomes(19, 1));
        assertThat(r.plannedMaintenanceMinutes()).isEqualTo(600);
        assertThat(r.measuredMinutes()).isEqualTo(42_600);
        // À mão: (42.600 − 44) / 42.600 × 100 = 99,896713… → 99,8967 (4 casas, HALF_UP).
        assertThat(r.availabilityPct()).isEqualByComparingTo("99.8967");
        assertThat(r.availabilityTargetMet()).contains(false);
        assertThat(r.availabilityDenominatorNote()).contains("EXCLUI a manutenção planejada");
    }

    @Test
    @DisplayName("medida a partir do lançamento: lançamento em 16/11 → mede 15 dias = 21.600 min")
    void measuredFromLaunch() {
        MonthlyContractReport.Report r = generate(0, LocalDate.of(2026, 11, 16), outcomes(19, 1));
        assertThat(r.measuredFrom()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(r.measuredMinutes()).isEqualTo(21_600);
        assertThat(r.availabilityPct()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("mês anterior ao lançamento é erro explícito, não disponibilidade presumida")
    void monthBeforeLaunchIsError() {
        assertThatThrownBy(() -> generate(0, LocalDate.of(2026, 12, 1), outcomes(19, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a partir do lançamento");
    }

    @Test
    @DisplayName("B1 — multa por chamado estourado (P2.15): S1 19+1 → 95,00% e 1×10% = 100.000; S2 49+1 → 50.000; total 150.000")
    void penaltyPerBreachedTicketHandCalculated() {
        // À mão (P2.15): S1 denominador = 19+1 = 20 → 95,00% < meta 99%;
        // multa = 1 estouro × 1.000.000 × 10% = 100.000 centavos.
        // S2 = 49+1 (ver caso da divergência). S3: denominador 0 →
        // indefinida, sem multa. S4/S5: sem estouro → sem multa.
        MonthlyContractReport.Report r = generate(0, LocalDate.of(2026, 1, 1), outcomes(19, 1));
        MonthlyContractReport.SeverityLine s1 = r.bySeverity().get("S1");
        assertThat(s1.adherencePct()).contains(new BigDecimal("95.00"));
        assertThat(s1.belowTarget()).isTrue();
        assertThat(s1.breachedResolutionCount()).isEqualTo(1);
        assertThat(s1.penaltyCents()).isEqualTo(100_000);
        assertThat(r.bySeverity().get("S3").adherencePct()).isEmpty();
        assertThat(r.bySeverity().get("S3").penaltyCents()).isZero();
        assertThat(r.bySeverity().get("S4").adherencePct()).contains(new BigDecimal("100.00"));
        assertThat(r.bySeverity().get("S4").penaltyCents()).isZero();
        assertThat(r.bySeverity().get("S5").penaltyCents()).isZero();
        assertThat(r.totalPenaltyCents()).isEqualTo(150_000);
    }

    @Test
    @DisplayName("B1 — DIVERGÊNCIA: aderência exatamente na meta (S2 49/50 = 98,00% = meta 98%) MAS 1 estouro → multa de 50.000 > 0")
    void adherenceAtTargetStillPaysPerBreach() {
        // É aqui que a fórmula antiga (1× por severidade abaixo da meta)
        // divergia da P2.15: abaixo da meta? NÃO (98,00% = 98%) → a
        // fórmula antiga dava 0. A P2.15 cobra por chamado estourado:
        // 1 × 1.000.000 × 5% = 50.000 centavos (R$ 500,00).
        MonthlyContractReport.Report r = generate(0, LocalDate.of(2026, 1, 1), outcomes(19, 1));
        MonthlyContractReport.SeverityLine s2 = r.bySeverity().get("S2");
        assertThat(s2.adherencePct()).contains(new BigDecimal("98.00"));
        assertThat(s2.adherenceTargetPct()).isEqualByComparingTo("98");
        assertThat(s2.belowTarget()).isFalse();
        assertThat(s2.breachedResolutionCount()).isEqualTo(1);
        assertThat(s2.penaltyCents()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("B1 — S2 40 no prazo + 10 estouros: denominador 50 → 80,00%; multa = 10 × 10.000 × 5% = R$ 5.000,00 = 500.000 centavos; total com S1 = 600.000")
    void penaltyTenBreachesHandCalculated() {
        // À mão: 10 estouros × (1.000.000 × 5%) = 10 × 50.000 = 500.000
        // centavos = R$ 5.000,00. S1 segue 1 estouro × 10% = 100.000.
        List<MonthlyContractReport.SeverityOutcome> out = List.of(
                new MonthlyContractReport.SeverityOutcome("S1", 19, 1, 0),
                new MonthlyContractReport.SeverityOutcome("S2", 40, 10, 0),
                new MonthlyContractReport.SeverityOutcome("S3", 0, 0, 0),
                new MonthlyContractReport.SeverityOutcome("S4", 20, 0, 0),
                new MonthlyContractReport.SeverityOutcome("S5", 10, 0, 0));
        MonthlyContractReport.Report r = generate(0, LocalDate.of(2026, 1, 1), out);
        assertThat(r.bySeverity().get("S2").adherencePct()).contains(new BigDecimal("80.00"));
        assertThat(r.bySeverity().get("S2").breachedResolutionCount()).isEqualTo(10);
        assertThat(r.bySeverity().get("S2").penaltyCents()).isEqualTo(500_000);
        assertThat(r.totalPenaltyCents()).isEqualTo(600_000);
    }

    @Test
    @DisplayName("B1 — denominador da P2.15 ignora resolvido SEM prazo: S3 8 no prazo + 2 estouros + 90 sem prazo → denominador 10 → 80,00% (não 8,00%); multa 2 × 2% = 40.000")
    void denominatorExcludesResolvedWithoutDue() {
        // À mão: se o denominador fosse TODOS os resolvidos (100), a
        // aderência seria 8,00% — a fórmula antiga. Na P2.15 só entra
        // quem tem prazo: 8/(8+2) = 80,00%. Multa: 2 × 1.000.000 × 2%
        // = 40.000. Total = S1 100.000 + S2 50.000 + S3 40.000 = 190.000.
        List<MonthlyContractReport.SeverityOutcome> out = List.of(
                new MonthlyContractReport.SeverityOutcome("S1", 19, 1, 0),
                new MonthlyContractReport.SeverityOutcome("S2", 49, 1, 0),
                new MonthlyContractReport.SeverityOutcome("S3", 8, 2, 90),
                new MonthlyContractReport.SeverityOutcome("S4", 20, 0, 0),
                new MonthlyContractReport.SeverityOutcome("S5", 10, 0, 0));
        MonthlyContractReport.Report r = generate(0, LocalDate.of(2026, 1, 1), out);
        assertThat(r.bySeverity().get("S3").adherencePct()).contains(new BigDecimal("80.00"));
        assertThat(r.bySeverity().get("S3").penaltyCents()).isEqualTo(40_000);
        assertThat(r.totalPenaltyCents()).isEqualTo(190_000);
    }

    @Test
    @DisplayName("b3 — bySeverity preserva a ordem S1–S5 do relatório")
    void bySeverityPreservesOrder() {
        MonthlyContractReport.Report r = generate(0, LocalDate.of(2026, 1, 1), outcomes(19, 1));
        assertThat(r.bySeverity().keySet()).containsExactly("S1", "S2", "S3", "S4", "S5");
    }

    @Test
    @DisplayName("nível STANDARD: disponibilidade calculada, mas sem veredito de meta de 99,9% (a meta é do Setor Público)")
    void standardLevelHasNoPublicSectorVerdict() {
        MonthlyContractReport.Report r = new MonthlyContractReport().generate(CONTRACT, TENANT, NOV_2026,
                "STANDARD", LocalDate.of(2026, 1, 1), 60, 0, 1_000_000, targets(), outcomes(20, 0));
        assertThat(r.availabilityPct()).isEqualByComparingTo("99.8611");
        assertThat(r.availabilityTargetMet()).isEmpty();
    }

    @Test
    @DisplayName("política sem os cinco alvos ou desfecho incompleto é recusada (V10_2 exige S1–S5)")
    void refusesIncompletePolicyOrOutcomes() {
        assertThatThrownBy(() -> new MonthlyContractReport().generate(CONTRACT, TENANT, NOV_2026,
                "PUBLIC_SECTOR", LocalDate.of(2026, 1, 1), 0, 0, 1_000_000,
                targets().subList(0, 4), outcomes(19, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cinco");
        assertThatThrownBy(() -> new MonthlyContractReport().generate(CONTRACT, TENANT, NOV_2026,
                "PUBLIC_SECTOR", LocalDate.of(2026, 1, 1), 0, 0, 1_000_000,
                targets(), outcomes(19, 1).subList(0, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
