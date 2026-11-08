package br.com.ziipsan.intelligence.losses.nr15;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LossManagementPlanTest {

    private static AnnualDeclaration declaration() {
        var service = Nr15Fixtures.declarationService(Nr15Fixtures.applicablePort(),
                Nr15Fixtures.yearSnapshots(), Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED));
        return service.declare(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR);
    }

    private static List<LossManagementPlan.MeteringEvolutionPoint> evolution() {
        return List.of(
                new LossManagementPlan.MeteringEvolutionPoint(2026,
                        LossManagementPlan.MeteringEvolutionPoint.Kind.MEASURED, 90.0, 95.0),
                new LossManagementPlan.MeteringEvolutionPoint(2027,
                        LossManagementPlan.MeteringEvolutionPoint.Kind.PLANNED, 92.0, 96.0));
    }

    private static List<LossManagementPlan.PlanAction> actions() {
        return List.of(new LossManagementPlan.PlanAction(
                "Pesquisa de vazamentos nos DMCs prioritarios",
                LossManagementPlan.ActionFocus.REAL,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1000.0,
                "Custo-beneficio sobre a fila de prioridade da ED-85 (declarado pelo prestador)"));
    }

    private static LossManagementPlan completePlan() {
        return completePlan(declaration());
    }

    private static LossManagementPlan completePlan(AnnualDeclaration declaration) {
        return Nr15PlanService.draftFromDiagnosis(UUID.randomUUID(), declaration, "MUN-TESTE-001",
                "Acompanhamento anual dos indices do diagnostico contra a linha de base",
                "ANUAL (declarado pelo prestador)",
                evolution(), actions(), List.of("tempo medio de reparo de vazamentos"),
                List.of(new LossManagementPlan.DeclaredTarget(2027, 80.0)),
                "Equipe propria de gestao de perdas", 5000.0);
    }

    @Test
    @DisplayName("Plano completo (art. 9o, I a VI): linha de base vem do diagnostico, custo total soma estrutura + acoes")
    void completePlanFromDiagnosis() {
        var declaration = declaration();
        var plan = completePlan(declaration);
        assertThat(plan.version()).isEqualTo(1);
        assertThat(plan.status()).isEqualTo(LossManagementPlan.Status.DRAFT);
        assertThat(plan.municipalityCode()).isEqualTo("MUN-TESTE-001");
        assertThat(plan.declarationId()).isEqualTo(declaration.id());
        // Linha de base = indicadores do diagnostico de 2026 (conferidos no teste de reconciliacao).
        assertThat(plan.baselineTotalLossesLPerConnDay())
                .isCloseTo(57600.0 * 1000 / 547500.0, within(0.001));
        assertThat(plan.baselineRealLossesLPerConnDayOrNull())
                .isCloseTo(49200.0 * 1000 / 547500.0, within(0.001));
        assertThat(plan.baselineApparentLossesLPerConnDayOrNull())
                .isCloseTo(8400.0 * 1000 / 547500.0, within(0.001));
        assertThat(plan.totalEstimatedCost()).isEqualTo(6000.0);
        assertThat(Nr15PlanService.missingItems(plan)).isEmpty();
    }

    @Test
    @DisplayName("acao sem justificativa tecnico-economica recusa na construcao (art. 9o, IV, b)")
    void actionWithoutJustificationRefuses() {
        assertThatThrownBy(() -> new LossManagementPlan.PlanAction(
                "Troca de hidrometros", LossManagementPlan.ActionFocus.APPARENT,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30), 2000.0, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa tecnico-economica");
        assertThatThrownBy(() -> new LossManagementPlan.PlanAction(
                "Troca de hidrometros", LossManagementPlan.ActionFocus.APPARENT,
                LocalDate.of(2027, 6, 30), LocalDate.of(2027, 1, 1), 2000.0, "justificativa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invertido");
    }

    @Test
    @DisplayName("Plano sem acoes ou sem indicador complementar: incompleto nomeado (art. 9o, IV e V)")
    void incompletePlanNamed() {
        assertThatThrownBy(() -> Nr15PlanService.draftFromDiagnosis(UUID.randomUUID(), declaration(),
                "MUN-TESTE-001", "Acompanhamento anual", "ANUAL (declarado)",
                evolution(), List.of(), List.of("indicador complementar"),
                List.of(), "Estrutura", 0.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NR15_PLAN_INCOMPLETE")
                .hasMessageContaining("ACTIONS");
        assertThatThrownBy(() -> Nr15PlanService.draftFromDiagnosis(UUID.randomUUID(), declaration(),
                "MUN-TESTE-001", "Acompanhamento anual", "ANUAL (declarado)",
                evolution(), actions(), List.of(),
                List.of(), "Estrutura", 0.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLEMENTARY_INDICATORS");
    }

    @Test
    @DisplayName("evolucao da medicao com um ano so (ou sem ponto medido) nao e evolucao (art. 9o, III)")
    void meteringEvolutionNeedsSeries() {
        var singleYear = List.of(new LossManagementPlan.MeteringEvolutionPoint(2026,
                LossManagementPlan.MeteringEvolutionPoint.Kind.MEASURED, 90.0, 95.0));
        assertThatThrownBy(() -> Nr15PlanService.draftFromDiagnosis(UUID.randomUUID(), declaration(),
                "MUN-TESTE-001", "Acompanhamento anual", "ANUAL (declarado)",
                singleYear, actions(), List.of("indicador complementar"),
                List.of(), "Estrutura", 0.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("METERING_EVOLUTION");
    }

    @Test
    @DisplayName("periodicidade nao declarada recusa: a NR 15 a remete ao ato da ERI, nada e presumido")
    void periodicityMustBeDeclared() {
        assertThatThrownBy(() -> Nr15PlanService.draftFromDiagnosis(UUID.randomUUID(), declaration(),
                "MUN-TESTE-001", "Acompanhamento anual", " ",
                evolution(), actions(), List.of("indicador complementar"),
                List.of(), "Estrutura", 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodicidade");
    }

    @Test
    @DisplayName("convergencia so contra alvo declarado: acima nao converge; sem alvo, desconhecida")
    void convergenceOnlyAgainstDeclaredTarget() {
        var plan = completePlan();
        var above = new LossManagementPlan.MonitoringEntry(2027, 0, 85.0, 10.0, 2.4, 91.0, 95.5);
        var below = new LossManagementPlan.MonitoringEntry(2027, 0, 75.0, 10.0, 2.1, 91.0, 95.5);
        var noTargetYear = new LossManagementPlan.MonitoringEntry(2028, 0, 70.0, 9.0, 2.0, 92.0, 96.0);
        var noRealData = new LossManagementPlan.MonitoringEntry(2027, 0, null, 10.0, null, 91.0, 95.5);
        assertThat(Nr15PlanService.assessConvergence(plan, above))
                .isEqualTo(Nr15PlanService.Convergence.NOT_CONVERGED);
        assertThat(Nr15PlanService.assessConvergence(plan, below))
                .isEqualTo(Nr15PlanService.Convergence.CONVERGED);
        assertThat(Nr15PlanService.assessConvergence(plan, noTargetYear))
                .isEqualTo(Nr15PlanService.Convergence.NO_TARGET_DECLARED);
        assertThat(Nr15PlanService.assessConvergence(plan, noRealData))
                .isEqualTo(Nr15PlanService.Convergence.NO_REAL_LOSS_DATA);
    }

    @Test
    @DisplayName("revisao (art. 9o, §1o): versao 2 ligada a anterior, com motivo e acoes revistas")
    void revisionLinksVersions() {
        var plan = completePlan();
        var revisedActions = List.of(new LossManagementPlan.PlanAction(
                "Intensificacao da pesquisa de vazamentos", LossManagementPlan.ActionFocus.BOTH,
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31), 2500.0,
                "Revisao apos acompanhamento anual sem convergencia (declarado)"));
        var revised = Nr15PlanService.revise(plan, revisedActions,
                "Acompanhamento de 2027 sem convergencia com o alvo declarado");
        assertThat(revised.version()).isEqualTo(2);
        assertThat(revised.revisionOfIdOrNull()).isEqualTo(plan.id());
        assertThat(revised.status()).isEqualTo(LossManagementPlan.Status.DRAFT);
        assertThat(revised.actions()).hasSize(1);
        assertThatThrownBy(() -> Nr15PlanService.revise(plan, revisedActions, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> Nr15PlanService.revise(plan, List.of(), "motivo declarado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acoes revistas");
    }
}
