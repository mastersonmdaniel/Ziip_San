package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plano ANUAL SELECTIVO de troca (II-5): ordena os hidrómetros
 * pontuados e selecciona sob restrição CONFIGURÁVEL — número máximo de
 * trocas no ano OU orçamento máximo do ano (valores {@code [A DEFINIR]}
 * do tenant, parâmetro {@code meter.renewal_annual_restriction}; este
 * núcleo não traz valor de fábrica nenhum).
 *
 * <p>Regras declaradas do plano:
 * <ul>
 *   <li><b>Elegibilidade:</b> só hidrómetros INSTALLED entram (troca é do
 *       parque instalado; os demais são contados como excluídos por
 *       estado). Piso de score opcional ({@code minScore == null} = sem
 *       piso); abaixo do piso, excluídos contados à parte. Hidrômetro
 *       repetido na entrada é erro explícito — o plano nunca carrega
 *       duplicados.</li>
 *   <li><b>Ordenação (determinística):</b> score total desc; desempate
 *       pela próxima verificação asc (quem vence primeiro troca
 *       primeiro; sem data vai para o fim do empate), depois
 *       {@code meterNumber} asc, depois {@code meterId} asc.</li>
 *   <li><b>Selecção por contagem:</b> os primeiros N da ordenação.</li>
 *   <li><b>Selecção por orçamento:</b> varredura na ordenação somando o
 *       custo de troca de cada linha; linha que não cabe no restante é
 *       excluída pela restrição e a varredura CONTINUA (uma linha mais
 *       barata, mais abaixo, ainda pode caber) — semântica declarada e
 *       provada em teste. Modo orçamento EXIGE custo em todas as linhas
 *       elegíveis (custo é entrada do tenant; ausente = erro explícito).</li>
 *   <li><b>Custo total estimado:</b> soma dos custos das linhas
 *       seleccionadas; {@code null} quando alguma linha seleccionada não
 *       tem custo (total desconhecido — nunca estimado por invenção);
 *       zero quando o plano está vazio.</li>
 * </ul>
 */
public final class AnnualRenewalPlan {
    private AnnualRenewalPlan() {}

    /** Restrição anual do plano — exactamente uma das duas formas. */
    public sealed interface RenewalRestriction {
        record ByCount(int maxReplacements) implements RenewalRestriction {
            public ByCount {
                if (maxReplacements < 0)
                    throw new IllegalArgumentException("Número máximo de trocas/ano não pode ser negativo: " + maxReplacements);
            }
        }
        record ByBudget(BigDecimal maxBudget) implements RenewalRestriction {
            public ByBudget {
                if (maxBudget == null || maxBudget.signum() < 0)
                    throw new IllegalArgumentException("Orçamento máximo do plano anual é obrigatório e não pode ser negativo");
            }
        }
    }

    /** Candidato ao plano: hidrómetro pontuado + avaliação do relógio (mesmo hidrómetro). */
    public record RenewalCandidate(RenewalScoreEngine.ScoredMeter scored,
                                   MetrologicalClock.ClockAssessment clock) {
        public RenewalCandidate {
            if (scored == null || clock == null)
                throw new IllegalArgumentException("Candidato exige hidrômetro pontuado e avaliação do relógio");
            if (!scored.snapshot().meterId().equals(clock.meterId()))
                throw new IllegalArgumentException("Score e relógio de hidrômetros diferentes não formam candidato");
        }
    }

    /** Linha do plano: posição (1..N na ordenação) e a fotografia que justifica a selecção. */
    public record PlanLine(int position, UUID meterId, String meterNumber, UUID unitId,
                           BigDecimal totalScore, RenewalScoreEngine.Criterion dominantCriterion,
                           MetrologicalClock.ClockStatus clockStatus, LocalDate nextVerificationOn,
                           BigDecimal replacementCost) {}

    /** Plano anual fechado: linhas seleccionadas + contagens de exclusão por motivo. */
    public record AnnualPlan(int planYear, RenewalRestriction restriction, BigDecimal minScore,
                             List<PlanLine> lines, int eligibleCount,
                             int excludedByStatusCount, int excludedBelowMinScoreCount,
                             int excludedByRestrictionCount, BigDecimal totalEstimatedCost) {
        public AnnualPlan { lines = List.copyOf(lines); }
    }

    /** Ordem do plano: score desc, próxima verificação asc (sem data por último), número asc, id asc. */
    public static final Comparator<RenewalCandidate> PLAN_ORDER =
            Comparator.comparing((RenewalCandidate c) -> c.scored().totalScore()).reversed()
                    .thenComparing(c -> c.clock().nextVerificationOn(),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(c -> c.scored().snapshot().meterNumber())
                    .thenComparing(c -> c.scored().snapshot().meterId());

    public static AnnualPlan build(int planYear, List<RenewalCandidate> candidates,
                                   RenewalRestriction restriction, BigDecimal minScore) {
        if (planYear <= 0) throw new IllegalArgumentException("Ano do plano é obrigatório");
        if (candidates == null) throw new IllegalArgumentException("Candidatos do plano são obrigatórios");
        if (restriction == null) throw new IllegalArgumentException(
            "Restrição anual do plano é obrigatória (parâmetro meter.renewal_annual_restriction) [A DEFINIR]");
        if (minScore != null && (minScore.signum() < 0 || minScore.compareTo(BigDecimal.ONE) > 0))
            throw new IllegalArgumentException("Piso de score deve estar entre 0 e 1: " + minScore);

        Set<UUID> seen = new HashSet<>();
        for (RenewalCandidate c : candidates)
            if (!seen.add(c.scored().snapshot().meterId()))
                throw new IllegalArgumentException(
                    "Hidrômetro duplicado na entrada do plano: " + c.scored().snapshot().meterId());

        int excludedByStatus = 0, excludedBelowMin = 0;
        List<RenewalCandidate> eligible = new ArrayList<>();
        for (RenewalCandidate c : candidates) {
            if (c.scored().snapshot().status() != MeterRenewalSnapshot.MeterParkStatus.INSTALLED) {
                excludedByStatus++; continue;
            }
            if (minScore != null && c.scored().totalScore().compareTo(minScore) < 0) {
                excludedBelowMin++; continue;
            }
            eligible.add(c);
        }
        eligible.sort(PLAN_ORDER);

        List<RenewalCandidate> selected = new ArrayList<>();
        int excludedByRestriction = 0;
        if (restriction instanceof RenewalRestriction.ByCount byCount) {
            for (RenewalCandidate c : eligible) {
                if (selected.size() < byCount.maxReplacements()) selected.add(c);
                else excludedByRestriction++;
            }
        } else if (restriction instanceof RenewalRestriction.ByBudget byBudget) {
            BigDecimal remaining = byBudget.maxBudget();
            for (RenewalCandidate c : eligible) {
                BigDecimal cost = c.scored().snapshot().replacementCost();
                if (cost == null) throw new IllegalArgumentException(
                    "Plano por orçamento exige custo de troca em todas as linhas elegíveis; "
                    + "sem custo: hidrômetro " + c.scored().snapshot().meterNumber());
                if (cost.compareTo(remaining) <= 0) { selected.add(c); remaining = remaining.subtract(cost); }
                else excludedByRestriction++;
            }
        }

        List<PlanLine> lines = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean allCosted = true;
        for (int i = 0; i < selected.size(); i++) {
            RenewalCandidate c = selected.get(i);
            var snap = c.scored().snapshot();
            lines.add(new PlanLine(i + 1, snap.meterId(), snap.meterNumber(), snap.unitId(),
                    c.scored().totalScore(), c.scored().dominantCriterion(),
                    c.clock().status(), c.clock().nextVerificationOn(), snap.replacementCost()));
            if (snap.replacementCost() == null) allCosted = false;
            else totalCost = totalCost.add(snap.replacementCost());
        }
        BigDecimal totalEstimated = lines.isEmpty() ? BigDecimal.ZERO : (allCosted ? totalCost : null);
        return new AnnualPlan(planYear, restriction, minScore, lines, eligible.size(),
                excludedByStatus, excludedBelowMin, excludedByRestriction, totalEstimated);
    }
}
