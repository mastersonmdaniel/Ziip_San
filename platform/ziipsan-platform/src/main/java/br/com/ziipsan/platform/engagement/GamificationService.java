package br.com.ziipsan.platform.engagement;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Gamificacao da ED-101, RESTRITA aos marcos da meta do proprio usuario
 * (aceite): avalia apenas o {@link GoalProgress} daquele usuario contra os
 * marcos que o TENANT configurou em {@code eng.gamification_milestones}.
 *
 * <p>O que NAO existe aqui, por desenho e nao por omissao: pontuacao
 * (nenhuma fonte fixa pontos), ranking/classificacao, liga, comparacao
 * entre usuarios e qualquer estrutura que identifique outro usuario. O
 * resultado carrega so codigos de marcos do proprio usuario.
 *
 * <p>Regras: modulo desligado ou sem adesao = as mesmas recusas do
 * {@link GoalService}; sem marcos configurados = resultado nomeado
 * {@code MILESTONES_NOT_CONFIGURED} com lista vazia (nunca marco
 * presumido); periodo aberto ou sem dado = nenhum marco (marco so sobre
 * periodo fechado com consumo conhecido).
 */
public final class GamificationService {

    public enum Status {EVALUATED, MILESTONES_NOT_CONFIGURED, PERIOD_OPEN, NO_DATA}

    public record GamificationResult(Status status, List<String> achievedMilestoneCodes) {
        public GamificationResult {
            achievedMilestoneCodes = achievedMilestoneCodes == null
                    ? List.of() : List.copyOf(achievedMilestoneCodes);
        }
    }

    private final EngagementParameters parameters;
    private final GoalService goalService;

    public GamificationService(EngagementParameters parameters, GoalService goalService) {
        this.parameters = Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        this.goalService = Objects.requireNonNull(goalService, "servico de metas e obrigatorio");
    }

    public GamificationResult evaluate(UUID tenantId, UUID customerId, GoalProgress progress) {
        Objects.requireNonNull(progress, "progresso e obrigatorio");
        goalService.requireActiveEnrollment(tenantId, customerId, progress.unitId());
        List<GamificationMilestones.MilestoneDefinition> definitions =
                GamificationMilestones.parse(parameters.gamificationMilestonesRaw(tenantId).orElse(null));
        if (definitions.isEmpty()) {
            return new GamificationResult(Status.MILESTONES_NOT_CONFIGURED, List.of());
        }
        if (progress.status() == GoalProgress.Status.NO_DATA) {
            return new GamificationResult(Status.NO_DATA, List.of());
        }
        if (!progress.periodClosed()) {
            return new GamificationResult(Status.PERIOD_OPEN, List.of());
        }
        List<String> achieved = definitions.stream()
                .filter(d -> progress.ratio().compareTo(d.fractionOfTarget()) <= 0)
                .map(GamificationMilestones.MilestoneDefinition::code)
                .toList();
        return new GamificationResult(Status.EVALUATED, achieved);
    }
}
