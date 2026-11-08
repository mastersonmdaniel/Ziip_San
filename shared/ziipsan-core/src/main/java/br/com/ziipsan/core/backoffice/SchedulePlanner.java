package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sugestao de programacao EXPLICAVEL e SEM custo de deslocamento (ED-36).
 *
 * <p>O que esta classe faz: filtra pelas restricoes DURAS CONFIGURADAS em
 * {@code sched.hard_constraints} (o conjunto entra em {@link #propose} e e
 * validado por {@link SchedParameters#hardConstraints} — SAFETY_QUALIFICATION
 * e ORDER_DEADLINE sao normativas e nao podem ser removidas pela
 * configuracao) e ordena/distribui pelas restricoes BRANDAS de
 * {@code sched.soft_weights} com ESCORE QUANTITATIVO (correcao D1 da revisao
 * cruzada de 08/11/2026 — antes os pesos eram interruptores: loadBalance nem
 * entrava no calculo e regionGrouping era so booleano):
 * <ul>
 *   <li><b>Ordenacao dentro da mesma prioridade:</b> gulosa por prazo
 *       ajustado — uma OS da mesma regiao da anterior recebe desconto de
 *       {@code regionGrouping × REGION_BONUS_MINUTES} no seu prazo para fins
 *       de ordenacao. Peso 0 = nenhum desconto (ordem pura por prazo); peso
 *       maior puxa OS mais distantes no prazo para junto do grupo.</li>
 *   <li><b>Escolha da equipe:</b> escore em minutos-equivalentes,
 *       {@code regionGrouping × REGION_BONUS_MINUTES × (1 se a ultima regiao
 *       da equipe e a da OS, senao 0) − loadBalance × carga da equipe (min)}.
 *       Maior escore vence; empate resolve pela ordem da lista de equipes
 *       (deterministico). Peso de carga 0 = a carga NAO influencia a escolha
 *       em nada; peso alto faz a equipe menos carregada vencer mesmo contra
 *       o bonus de continuidade regional.</li>
 * </ul>
 *
 * <p><b>Prazo contra o inicio EFETIVO (correcao D2):</b> a restricao dura
 * ORDER_DEADLINE e conferida contra o fim estimado a partir do inicio
 * efetivo de cada equipe (maximo entre o inicio da janela e o fim da fila
 * daquela equipe no plano), nunca so contra o inicio da janela. Se nenhuma
 * equipe entrega a OS dentro do prazo (e da janela, quando configurada), a
 * OS e EXCLUIDA com o motivo — a proposta nunca nasce afirmando "prazo
 * atendido" para um fim estimado alem do prazo.
 *
 * <p>A proposta nasce NAO CONFIRMADA: so vale depois de
 * {@link Proposal#confirm(UUID)} — "sempre confirmado por pessoa"
 * (acceptance).
 *
 * <p>O que esta classe NAO faz: custo/tempo de deslocamento sobre o grafo
 * viario (pgRouting). Isso esta bloqueado por desenho em
 * {@link RoutingGate}: o ADR-0010 (ajuste do dono de 29/10/2026) condiciona
 * a roteirizacao de equipes a ADR proprio, e nenhum ADR de liberacao foi
 * localizado nas fontes — pendencia condicionada declarada no relatorio.
 */
public final class SchedulePlanner {

    /**
     * Valor, em minutos-equivalentes, de 1,0 de peso de agrupamento regional:
     * e o quanto de prazo (na ordenacao) ou de carga (na escolha da equipe)
     * a continuidade regional compensa por unidade de peso. Constante de
     * heuristica do planejador — os PESOS sao do tenant
     * ({@code sched.soft_weights}); esta escala so da a unidade comum.
     */
    static final double REGION_BONUS_MINUTES = 30.0;

    public record Candidate(UUID orderId, ServiceOrderEnums.Priority priority, LocalDateTime dueAt,
                            LocalDateTime windowStart, LocalDateTime windowEnd, int estimatedMinutes,
                            String regionKey, boolean safetyQualified, boolean equipmentAvailable) {
        public Candidate {
            Objects.requireNonNull(orderId, "OS candidata e obrigatoria");
            Objects.requireNonNull(priority, "prioridade e obrigatoria");
            Objects.requireNonNull(dueAt, "prazo da OS (P1.31) e obrigatorio");
            Objects.requireNonNull(windowStart, "inicio da janela e obrigatorio");
            Objects.requireNonNull(windowEnd, "fim da janela e obrigatorio");
            if (!windowEnd.isAfter(windowStart)) throw new IllegalArgumentException("janela da candidata invalida");
            if (estimatedMinutes <= 0) throw new IllegalArgumentException("duracao estimada deve ser positiva");
            if (regionKey == null || regionKey.isBlank()) throw new IllegalArgumentException("regiao da OS e obrigatoria para o agrupamento");
        }
    }

    public record Ranked(UUID orderId, UUID crewId, LocalDateTime proposedStart, List<String> reasons) {}
    public record Excluded(UUID orderId, List<String> reasons) {}

    public record Proposal(List<Ranked> ranked, List<Excluded> excluded, boolean confirmed, UUID confirmedBy) {
        public Proposal confirm(UUID personId) {
            Objects.requireNonNull(personId, "quem confirma a sugestao e obrigatorio");
            if (confirmed) throw new IllegalStateException("sugestao ja confirmada");
            return new Proposal(ranked, excluded, true, personId);
        }
    }

    private SchedulePlanner() {}

    /**
     * @param crews equipes disponiveis (ids de equipe/pessoa lider — UUID opaco ao nucleo)
     * @param currentLoadMinutes carga atual de cada equipe, em minutos (equilibrio de carga parte dela)
     * @param hardConstraints conjunto CONFIGURADO de {@code sched.hard_constraints} do tenant
     *        (validado aqui: ausencia e erro explicito; remover SAFETY_QUALIFICATION
     *        ou ORDER_DEADLINE e recusado, nunca afrouxado)
     */
    public static Proposal propose(List<Candidate> candidates, List<UUID> crews, Map<UUID, Integer> currentLoadMinutes,
                                   SchedParameters.SoftWeights weights,
                                   Set<SchedParameters.HardConstraint> hardConstraints) {
        Objects.requireNonNull(candidates, "candidatas sao obrigatorias");
        if (crews == null || crews.isEmpty()) throw new IllegalArgumentException("sugestao exige ao menos uma equipe");
        Objects.requireNonNull(weights, SchedParameters.SOFT_WEIGHTS + " e obrigatorio");
        Set<SchedParameters.HardConstraint> hard = SchedParameters.hardConstraints(hardConstraints);
        boolean enforceDeadline = hard.contains(SchedParameters.HardConstraint.ORDER_DEADLINE);
        boolean enforceWindow = hard.contains(SchedParameters.HardConstraint.CUSTOMER_WINDOW);
        Map<UUID, Integer> load = new HashMap<>();
        for (UUID c : crews) load.put(c, currentLoadMinutes == null ? 0 : currentLoadMinutes.getOrDefault(c, 0));

        List<Candidate> feasible = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (Candidate c : candidates) {
            List<String> reasons = new ArrayList<>();
            if (hard.contains(SchedParameters.HardConstraint.SAFETY_QUALIFICATION) && !c.safetyQualified())
                reasons.add("habilitacao SST nao conferida (restricao dura " + SchedParameters.HARD_CONSTRAINTS + ")");
            if (hard.contains(SchedParameters.HardConstraint.EQUIPMENT_WHEN_ED59) && !c.equipmentAvailable())
                reasons.add("equipamento indisponivel (restricao dura EQUIPMENT_WHEN_ED59 de " + SchedParameters.HARD_CONSTRAINTS + "; ED-59)");
            // Pre-filtro SOUND do prazo: o inicio efetivo nunca e antes do inicio da janela,
            // logo se nem comecando na abertura da janela o prazo e cumprido, nenhuma equipe salva.
            // A conferencia que vale e a do inicio efetivo, na atribuicao (D2).
            if (enforceDeadline && c.windowStart().plusMinutes(c.estimatedMinutes()).isAfter(c.dueAt()))
                reasons.add("janela + duracao estimada estouram o prazo da OS (restricao dura ORDER_DEADLINE)");
            if (enforceWindow && c.windowEnd().isBefore(c.windowStart().plusMinutes(c.estimatedMinutes())))
                reasons.add("duracao estimada nao cabe na janela do usuario (restricao dura CUSTOMER_WINDOW)");
            if (reasons.isEmpty()) feasible.add(c); else excluded.add(new Excluded(c.orderId(), List.copyOf(reasons)));
        }

        // Ordenacao primaria: prioridade (urgencia sempre no topo — sched.priority_weights)
        // e prazo mais cedo. Agrupamento por regiao atua DENTRO da mesma prioridade, com peso.
        feasible.sort(Comparator.comparingInt((Candidate c) -> priorityRank(c.priority()))
                .thenComparing(Candidate::dueAt).thenComparing(c -> c.orderId().toString()));
        List<Candidate> grouped = groupByRegionWithinPriority(feasible, weights);

        List<Ranked> ranked = new ArrayList<>();
        Map<UUID, LocalDateTime> crewFreeAt = new LinkedHashMap<>();
        Map<UUID, String> crewLastRegion = new HashMap<>();
        for (UUID crew : crews) crewFreeAt.put(crew, null);
        String previousRegion = null;
        for (Candidate c : grouped) {
            // Para cada equipe: inicio EFETIVO (fila da equipe no plano x janela) e viabilidade
            // contra as duras configuradas. So equipe viavel entra no escore (D2).
            UUID bestCrew = null;
            LocalDateTime bestStart = null;
            double bestScore = 0;
            int bestLoad = 0;
            double bestRegionPoints = 0;
            LocalDateTime earliestEnd = null;
            LocalDateTime earliestStart = null;
            boolean anyWindowOk = false;
            boolean anyDeadlineOk = false;
            for (UUID crew : crews) {
                LocalDateTime freeAt = crewFreeAt.get(crew);
                LocalDateTime start = freeAt == null || freeAt.isBefore(c.windowStart()) ? c.windowStart() : freeAt;
                LocalDateTime end = start.plusMinutes(c.estimatedMinutes());
                if (earliestEnd == null || end.isBefore(earliestEnd)) { earliestEnd = end; earliestStart = start; }
                boolean windowOk = !enforceWindow || !end.isAfter(c.windowEnd());
                boolean deadlineOk = !enforceDeadline || !end.isAfter(c.dueAt());
                if (windowOk) anyWindowOk = true;
                if (deadlineOk) anyDeadlineOk = true;
                if (!windowOk || !deadlineOk) continue;
                double regionPoints = c.regionKey().equals(crewLastRegion.get(crew)) ? weights.regionGrouping() * REGION_BONUS_MINUTES : 0.0;
                double score = regionPoints - weights.loadBalance() * load.get(crew);
                if (bestCrew == null || score > bestScore) {
                    bestCrew = crew; bestStart = start; bestScore = score; bestLoad = load.get(crew); bestRegionPoints = regionPoints;
                }
            }
            if (bestCrew == null) {
                // Nunca propor violando dura: exclui com o motivo preciso (prazo contra o inicio efetivo).
                if (enforceDeadline && !anyDeadlineOk)
                    excluded.add(new Excluded(c.orderId(), List.of("inicio efetivo " + earliestStart
                            + " (apos a fila da equipe) leva o fim estimado a " + earliestEnd
                            + ", apos o prazo " + c.dueAt() + " (restricao dura ORDER_DEADLINE)")));
                else if (enforceWindow && !anyWindowOk)
                    excluded.add(new Excluded(c.orderId(), List.of("sem equipe com tempo na janela do usuario (restricao dura CUSTOMER_WINDOW)")));
                else
                    excluded.add(new Excluded(c.orderId(), List.of("sem equipe viavel pelas restricoes duras configuradas")));
                continue;
            }
            LocalDateTime bestEnd = bestStart.plusMinutes(c.estimatedMinutes());
            List<String> reasons = new ArrayList<>();
            reasons.add("prioridade " + c.priority() + " (peso " + priorityRank(c.priority()) + " de " + SchedParameters.PRIORITY_WEIGHTS + ")");
            if (enforceDeadline)
                reasons.add("prazo " + c.dueAt() + " atendido: inicio efetivo " + bestStart + ", fim estimado " + bestEnd);
            if (weights.regionGrouping() > 0 && c.regionKey().equals(previousRegion))
                reasons.add("agrupada com a OS anterior na regiao " + c.regionKey() + " (peso " + weights.regionGrouping()
                        + "; desconto de " + fmt(weights.regionGrouping() * REGION_BONUS_MINUTES) + " min no prazo de ordenacao)");
            reasons.add("equipe " + bestCrew + " por escore: continuidade regional " + fmt(bestRegionPoints)
                    + " min-equiv − carga " + bestLoad + " min × peso " + fmt(weights.loadBalance())
                    + " = escore " + fmt(bestScore));
            ranked.add(new Ranked(c.orderId(), bestCrew, bestStart, List.copyOf(reasons)));
            load.put(bestCrew, bestLoad + c.estimatedMinutes());
            crewFreeAt.put(bestCrew, bestEnd);
            crewLastRegion.put(bestCrew, c.regionKey());
            previousRegion = c.regionKey();
        }
        return new Proposal(List.copyOf(ranked), List.copyOf(excluded), false, null);
    }

    /**
     * Sequenciamento por regiao DENTRO da mesma prioridade, com peso quantitativo:
     * a cada passo, escolhe a candidata de menor prazo AJUSTADO, onde o prazo de
     * uma OS da mesma regiao da anterior e descontado de
     * {@code regionGrouping × REGION_BONUS_MINUTES}. Peso 0 devolve a ordem pura
     * por prazo; quanto maior o peso, mais longe no prazo uma OS do grupo pode
     * estar e ainda assim ser puxada para junto.
     */
    private static List<Candidate> groupByRegionWithinPriority(List<Candidate> sorted, SchedParameters.SoftWeights weights) {
        double bonus = weights.regionGrouping() * REGION_BONUS_MINUTES;
        if (bonus <= 0) return sorted;
        List<Candidate> out = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int j = i;
            while (j < sorted.size() && sorted.get(j).priority() == sorted.get(i).priority()) j++;
            List<Candidate> band = new ArrayList<>(sorted.subList(i, j));
            String previous = null;
            while (!band.isEmpty()) {
                Candidate next = null;
                double nextKey = 0;
                for (Candidate c : band) {
                    double key = epochMinute(c.dueAt()) - (c.regionKey().equals(previous) ? bonus : 0.0);
                    if (next == null || key < nextKey
                            || (key == nextKey && (c.dueAt().isBefore(next.dueAt())
                                || (c.dueAt().equals(next.dueAt()) && c.orderId().toString().compareTo(next.orderId().toString()) < 0)))) {
                        next = c; nextKey = key;
                    }
                }
                band.remove(next);
                out.add(next);
                previous = next.regionKey();
            }
            i = j;
        }
        return out;
    }

    private static double epochMinute(LocalDateTime t) {
        return t.toEpochSecond(ZoneOffset.UTC) / 60.0;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Urgencia sempre no topo (catalogo de parametros, sched.priority_weights). */
    static int priorityRank(ServiceOrderEnums.Priority p) {
        return switch (p) { case URGENT -> 0; case HIGH -> 1; case NORMAL -> 2; case LOW -> 3; };
    }
}
