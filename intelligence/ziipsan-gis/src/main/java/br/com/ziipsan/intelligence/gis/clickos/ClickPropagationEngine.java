package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickOutcome;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickPropagationConfig;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationResult;
import br.com.ziipsan.intelligence.gis.maneuver.IsolationEngine;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverGraph;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FlowDirection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkArc;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Motor de propagação do clique (II-1) sobre o retrato da GIS-03.
 *
 * <p>Sem limite de profundidade, o traçado dirigido é o do
 * {@code ManeuverGraph} da GIS-11 — chamado, não reimplementado
 * (a igualdade é provada em teste). Com {@code maxHops}, corre uma
 * BFS multi-fonte por profundidade sobre a MESMA adjacência
 * dirigida que o ManeuverGraph constrói (arcos em operação;
 * {@code WITH_GEOMETRY}/{@code BOTH} geram nó1→nó2,
 * {@code AGAINST_GEOMETRY}/{@code BOTH} geram nó2→nó1,
 * {@code UNKNOWN} não gera aresta dirigida — não se presume
 * sentido), porque o ManeuverGraph não expõe profundidade.
 *
 * <p>O modo {@code ISOLATION} delega integralmente na
 * {@code IsolationEngine}: válvulas a fechar, área e UCs são os
 * dela; a II-1 só traduz o resultado para o desfecho do clique.
 */
public final class ClickPropagationEngine {

    private ClickPropagationEngine() {
    }

    public static PropagationResult propagate(NetworkSnapshot snapshot, Target clicked,
                                              PropagationMode mode, ClickPropagationConfig config) {
        if (config == null) config = ClickPropagationConfig.proofDefaults();
        if (mode == PropagationMode.ISOLATION) return propagateIsolation(snapshot, clicked, config);

        List<UUID> seeds = seedsOf(snapshot, clicked);
        List<UUID> nodes;
        boolean hopsTruncated = false;
        if (config.maxHops() == null) {
            ManeuverGraph graph = new ManeuverGraph(snapshot);
            nodes = switch (mode) {
                case DOWNSTREAM -> clicked.arcId() != null
                        ? graph.downstreamOfArc(clicked.arcId()) : graph.downstreamNodes(clicked.nodeId());
                case UPSTREAM -> clicked.arcId() != null
                        ? graph.upstreamOfArc(clicked.arcId()) : graph.upstreamNodes(clicked.nodeId());
                case BOTH -> merge(
                        clicked.arcId() != null ? graph.downstreamOfArc(clicked.arcId()) : graph.downstreamNodes(clicked.nodeId()),
                        clicked.arcId() != null ? graph.upstreamOfArc(clicked.arcId()) : graph.upstreamNodes(clicked.nodeId()));
                case ISOLATION -> throw new IllegalStateException("ISOLATION tratado acima");
            };
        } else {
            LimitedTrace t = limitedTrace(snapshot, seeds, mode, config.maxHops());
            nodes = t.nodes();
            hopsTruncated = t.truncated();
        }
        return assemble(snapshot, clicked, mode, nodes, List.of(), null, config, hopsTruncated);
    }

    // ------------------------------------------------------------------ ISOLATION

    private static PropagationResult propagateIsolation(NetworkSnapshot snapshot, Target clicked,
                                                        ClickPropagationConfig config) {
        IsolationEngine.IsolationResult r = IsolationEngine.isolate(snapshot, clicked);
        if (!r.isolable()) {
            return new PropagationResult(clicked, PropagationMode.ISOLATION,
                    List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                    List.of(), List.of(), List.of(),
                    r.failure() == null ? null : r.failure().name(),
                    false, false, ClickOutcome.NOT_ISOLABLE);
        }
        boolean unitLimitExceeded = config.maxAffectedUnits() != null
                && r.affectedUnitIds().size() > config.maxAffectedUnits();
        List<UUID> essential = r.essentialConnections().stream()
                .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList();
        List<UUID> sensitive = r.affectedConnections().stream().filter(FunctionalConnection::sensitive)
                .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList();
        ClickOutcome outcome = outcomeFor(r.affectedUnitIds(),
                r.affectedNodeIds().size() <= seedsOf(snapshot, clicked).size()
                        && r.affectedConnections().isEmpty());
        return new PropagationResult(clicked, PropagationMode.ISOLATION,
                r.affectedNodeIds(), r.affectedArcIds(), r.affectedConnections(),
                r.affectedUnitIds(),
                r.affectedConnections().stream().map(FunctionalConnection::serviceConnectionId).distinct().toList(),
                r.totalEconomies(), essential, sensitive,
                r.valvesToClose().stream().map(v -> v.code()).toList(), null,
                unitLimitExceeded, unitLimitExceeded, outcome);
    }

    // ------------------------------------------------------------------ montagem comum

    private static PropagationResult assemble(NetworkSnapshot snapshot, Target clicked, PropagationMode mode,
                                              List<UUID> nodes, List<String> valves, String failure,
                                              ClickPropagationConfig config, boolean hopsTruncated) {
        Set<UUID> nodeSet = new HashSet<>(nodes);
        List<UUID> arcs = snapshot.serviceArcs().stream()
                .filter(a -> nodeSet.contains(a.node1Id()) && nodeSet.contains(a.node2Id()))
                .sorted(Comparator.comparing(NetworkArc::code))
                .map(NetworkArc::id).toList();
        Set<UUID> arcSet = new HashSet<>(arcs);
        List<FunctionalConnection> conns = snapshot.connections().stream()
                .filter(c -> (c.tapNodeId() != null && nodeSet.contains(c.tapNodeId()))
                        || (c.arcId() != null && arcSet.contains(c.arcId())))
                .sorted(IsolationEngine.NOTICE_ORDER)
                .toList();
        List<UUID> units = conns.stream().flatMap(c -> c.consumerUnitIds().stream()).distinct().toList();
        boolean unitLimitExceeded = config.maxAffectedUnits() != null
                && units.size() > config.maxAffectedUnits();
        boolean isolated = nodes.size() <= seedsOf(snapshot, clicked).size();
        return new PropagationResult(clicked, mode, nodes, arcs, conns, units,
                conns.stream().map(FunctionalConnection::serviceConnectionId).distinct().toList(),
                conns.stream().mapToInt(FunctionalConnection::economies).sum(),
                conns.stream().filter(FunctionalConnection::essential)
                        .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList(),
                conns.stream().filter(FunctionalConnection::sensitive)
                        .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList(),
                valves, failure, hopsTruncated || unitLimitExceeded, unitLimitExceeded,
                outcomeFor(units, isolated));
    }

    private static ClickOutcome outcomeFor(List<UUID> units, boolean isolated) {
        if (!units.isEmpty()) return ClickOutcome.WITH_AFFECTED_UNITS;
        return isolated ? ClickOutcome.ISOLATED_ELEMENT : ClickOutcome.WITHOUT_AFFECTED_UNITS;
    }

    // ------------------------------------------------------------------ BFS com limite

    private record LimitedTrace(List<UUID> nodes, boolean truncated) {
    }

    /**
     * BFS multi-fonte por profundidade. Fontes = pontas do elemento
     * clicado (profundidade 0). Em {@code BOTH}, corre jusante e
     * montante e funde (jusante primeiro), como no caminho sem limite.
     */
    private static LimitedTrace limitedTrace(NetworkSnapshot snapshot, List<UUID> seeds,
                                             PropagationMode mode, int maxHops) {
        Map<UUID, List<UUID>> down = new HashMap<>(), up = new HashMap<>();
        for (NetworkArc a : snapshot.serviceArcs()) {
            if (a.flowDirection() == FlowDirection.WITH_GEOMETRY || a.flowDirection() == FlowDirection.BOTH)
                down.computeIfAbsent(a.node1Id(), k -> new ArrayList<>()).add(a.node2Id());
            if (a.flowDirection() == FlowDirection.AGAINST_GEOMETRY || a.flowDirection() == FlowDirection.BOTH)
                down.computeIfAbsent(a.node2Id(), k -> new ArrayList<>()).add(a.node1Id());
        }
        down.forEach((from, tos) -> tos.forEach(to ->
                up.computeIfAbsent(to, k -> new ArrayList<>()).add(from)));

        boolean[] truncated = {false};
        List<UUID> result;
        switch (mode) {
            case DOWNSTREAM -> result = bfsLimited(seeds, down, maxHops, truncated);
            case UPSTREAM -> result = bfsLimited(seeds, up, maxHops, truncated);
            case BOTH -> {
                List<UUID> d = bfsLimited(seeds, down, maxHops, truncated);
                List<UUID> u = bfsLimited(seeds, up, maxHops, truncated);
                result = merge(d, u);
            }
            default -> throw new IllegalStateException("modo sem BFS limitada: " + mode);
        }
        return new LimitedTrace(result, truncated[0]);
    }

    private static List<UUID> bfsLimited(List<UUID> seeds, Map<UUID, List<UUID>> adj,
                                         int maxHops, boolean[] truncated) {
        List<UUID> order = new ArrayList<>();
        Map<UUID, Integer> depth = new HashMap<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        for (UUID s : seeds) if (depth.putIfAbsent(s, 0) == null) queue.add(s);
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            order.add(cur);
            int d = depth.get(cur);
            for (UUID next : adj.getOrDefault(cur, List.of())) {
                if (depth.containsKey(next)) continue;
                if (d + 1 > maxHops) { truncated[0] = true; continue; }
                depth.put(next, d + 1);
                queue.add(next);
            }
        }
        return List.copyOf(order);
    }

    // ------------------------------------------------------------------ utilidades

    private static List<UUID> seedsOf(NetworkSnapshot snapshot, Target clicked) {
        if (clicked.arcId() != null) {
            NetworkArc a = snapshot.arcsById().get(clicked.arcId());
            if (a == null) throw new IllegalArgumentException("arco fora do retrato: " + clicked.arcId());
            return List.of(a.node1Id(), a.node2Id());
        }
        if (!snapshot.nodesById().containsKey(clicked.nodeId()))
            throw new IllegalArgumentException("no fora do retrato: " + clicked.nodeId());
        return List.of(clicked.nodeId());
    }

    private static List<UUID> merge(List<UUID> first, List<UUID> second) {
        LinkedHashSet<UUID> out = new LinkedHashSet<>(first);
        out.addAll(second);
        return List.copyOf(out);
    }
}
