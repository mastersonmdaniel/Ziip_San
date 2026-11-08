package br.com.ziipsan.intelligence.losses.nr15;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico do Plano de Gestao da NR 15 (ED-78): confere o conteudo
 * minimo do art. 9o, monta o Plano a partir do diagnostico
 * (declaracao anual), avalia a convergencia dos acompanhamentos e
 * produz revisoes ligadas.
 *
 * <p>Completude (art. 9o, I a VI) - item faltante e NOMEADO:
 * diagnostico ligado; linha de base do diagnostico no
 * acompanhamento; serie de evolucao da hidrometacao/macromedicao
 * com ao menos um ponto MEDIDO (a base) e um segundo ano (a
 * evolucao); ao menos uma acao completa; ao menos um indicador
 * complementar; estrutura operacional declarada. A estrutura com
 * custo zero so existe quando o prestador a DECLARA (custo e campo
 * obrigatorio do registro, nao omissao).
 *
 * <p>Convergencia (art. 9o, §1o): so e avaliada contra alvo
 * DECLARADO para o ano (ato da ERI/prestador). Sem alvo, o resultado
 * e {@link Convergence#NO_TARGET_DECLARED} - nunca convergencia
 * presumida. {@code NOT_CONVERGED} e o gatilho declarado da revisao.
 */
public final class Nr15PlanService {

    public enum Convergence { CONVERGED, NOT_CONVERGED, NO_TARGET_DECLARED, NO_REAL_LOSS_DATA }

    /** Itens do conteudo minimo do art. 9o que podem faltar. */
    public enum MissingItem {
        DIAGNOSIS, BASELINE_MONITORING, METERING_EVOLUTION, ACTIONS,
        COMPLEMENTARY_INDICATORS, OPERATIONAL_STRUCTURE
    }

    private Nr15PlanService() {
    }

    /** Lista os itens do art. 9o ausentes ou insuficientes no Plano. */
    public static List<MissingItem> missingItems(LossManagementPlan plan) {
        Objects.requireNonNull(plan, "Plano obrigatorio na conferencia");
        List<MissingItem> missing = new ArrayList<>();
        // I e II ja sao guardas do registro (declaracao, descricao,
        // periodicidade); a conferencia aqui e a suficiencia de serie/acoes.
        boolean hasMeasured = plan.meteringEvolution().stream()
                .anyMatch(p -> p.kind() == LossManagementPlan.MeteringEvolutionPoint.Kind.MEASURED);
        long distinctYears = plan.meteringEvolution().stream()
                .map(LossManagementPlan.MeteringEvolutionPoint::year).distinct().count();
        if (!hasMeasured || distinctYears < 2) missing.add(MissingItem.METERING_EVOLUTION);
        if (plan.actions().isEmpty()) missing.add(MissingItem.ACTIONS);
        if (plan.complementaryIndicators().stream().allMatch(s -> s == null || s.isBlank())) {
            missing.add(MissingItem.COMPLEMENTARY_INDICATORS);
        }
        if (plan.operationalStructure() == null || plan.operationalStructure().isBlank()) {
            missing.add(MissingItem.OPERATIONAL_STRUCTURE);
        }
        return List.copyOf(missing);
    }

    /** Exige o Plano completo; recusa nomeando os itens faltantes do art. 9o. */
    public static void requireComplete(LossManagementPlan plan) {
        List<MissingItem> missing = missingItems(plan);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("NR15_PLAN_INCOMPLETE: conteudo minimo do art. 9o da NR 15"
                    + " ausente/insuficiente: " + missing);
        }
    }

    /**
     * Monta a versao 1 do Plano sobre o diagnostico (declaracao
     * anual): a linha de base sao os indices do diagnostico, sem
     * copia manual de numeros.
     */
    public static LossManagementPlan draftFromDiagnosis(
            UUID id, AnnualDeclaration declaration, String municipalityCode,
            String monitoringDescription, String monitoringPeriodicityDeclared,
            List<LossManagementPlan.MeteringEvolutionPoint> meteringEvolution,
            List<LossManagementPlan.PlanAction> actions,
            List<String> complementaryIndicators,
            List<LossManagementPlan.DeclaredTarget> declaredTargets,
            String operationalStructure, double operationalCostEstimate) {
        Objects.requireNonNull(declaration, "diagnostico (declaracao anual) obrigatorio para o Plano");
        Nr15Indicators diagnosis = declaration.indicators();
        LossManagementPlan plan = new LossManagementPlan(
                id, declaration.tenantId(), municipalityCode, 1, declaration.baseYear(),
                declaration.id(), declaration.agencyCode(),
                diagnosis.totalLossesLPerConnDay(),
                diagnosis.realLossesLPerConnDayOrNull(),
                diagnosis.apparentLossesLPerConnDayOrNull(),
                monitoringDescription, monitoringPeriodicityDeclared,
                meteringEvolution, actions, complementaryIndicators, declaredTargets,
                operationalStructure, operationalCostEstimate,
                LossManagementPlan.Status.DRAFT, null, null);
        requireComplete(plan);
        return plan;
    }

    /** Avalia a convergencia de um acompanhamento anual contra o alvo declarado. */
    public static Convergence assessConvergence(LossManagementPlan plan,
                                                LossManagementPlan.MonitoringEntry entry) {
        Objects.requireNonNull(plan, "Plano obrigatorio na avaliacao de convergencia");
        Objects.requireNonNull(entry, "acompanhamento obrigatorio na avaliacao de convergencia");
        var target = plan.targetFor(entry.year());
        if (target.isEmpty()) return Convergence.NO_TARGET_DECLARED;
        if (entry.realLossesLPerConnDayOrNull() == null) return Convergence.NO_REAL_LOSS_DATA;
        return entry.realLossesLPerConnDayOrNull() <= target.get().realLossesLPerConnDayTarget()
                ? Convergence.CONVERGED : Convergence.NOT_CONVERGED;
    }

    /**
     * Revisao do Plano (art. 9o, §1o): nova versao DRAFT ligada a
     * anterior, com motivo e as acoes revistas; os demais conteudos
     * sao herdados (o chamador os substitui editando antes de
     * submeter a nova versao, na fiacao).
     */
    public static LossManagementPlan revise(LossManagementPlan current,
                                            List<LossManagementPlan.PlanAction> revisedActions,
                                            String reason) {
        Objects.requireNonNull(current, "Plano vigente obrigatorio na revisao");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("revisao do Plano sem motivo (art. 9o, §1o)");
        }
        if (revisedActions == null || revisedActions.isEmpty()) {
            throw new IllegalArgumentException("revisao do Plano sem acoes revistas (art. 9o, §1o:"
                    + " ajuste das acoes em forma, conteudo e quantidade)");
        }
        LossManagementPlan revised = new LossManagementPlan(
                UUID.randomUUID(), current.tenantId(), current.municipalityCode(),
                current.version() + 1, current.baseYear(), current.declarationId(),
                current.agencyCode(), current.baselineTotalLossesLPerConnDay(),
                current.baselineRealLossesLPerConnDayOrNull(),
                current.baselineApparentLossesLPerConnDayOrNull(),
                current.monitoringDescription(), current.monitoringPeriodicityDeclared(),
                current.meteringEvolution(), revisedActions,
                current.complementaryIndicators(), current.declaredTargets(),
                current.operationalStructure(), current.operationalCostEstimate(),
                LossManagementPlan.Status.DRAFT, current.id(), reason);
        requireComplete(revised);
        return revised;
    }
}
