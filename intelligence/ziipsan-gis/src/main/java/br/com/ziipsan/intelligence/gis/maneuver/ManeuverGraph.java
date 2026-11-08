package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FlowDirection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkArc;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traçados montante/jusante (GIS-11) sobre a topologia persistida:
 * BFS no grafo DIRIGIDO pelo {@code flow_direction} dos arcos em
 * operação. {@code UNKNOWN} não gera aresta dirigida (não se presume
 * sentido); {@code BOTH} gera os dois sentidos.
 *
 * <p>Determinismo: vizinhos visitados na ordem dos arcos do retrato
 * e resultados devolvidos ordenados pela ordem de descoberta da BFS
 * a partir do alvo — o mesmo retrato dá sempre o mesmo traçado
 * (teste com resultado conhecido).
 */
public final class ManeuverGraph {

    private final NetworkSnapshot snapshot;
    private final Map<UUID, List<UUID>> downstreamAdj = new HashMap<>();
    private final Map<UUID, List<UUID>> upstreamAdj = new HashMap<>();
    private final Map<UUID, List<NetworkArc>> undirectedAdj = new HashMap<>();

    public ManeuverGraph(NetworkSnapshot snapshot) {
        this.snapshot = snapshot;
        for (NetworkArc a : snapshot.serviceArcs()) {
            undirectedAdj.computeIfAbsent(a.node1Id(), k -> new ArrayList<>()).add(a);
            undirectedAdj.computeIfAbsent(a.node2Id(), k -> new ArrayList<>()).add(a);
            if (a.flowDirection() == FlowDirection.WITH_GEOMETRY || a.flowDirection() == FlowDirection.BOTH) {
                link(a.node1Id(), a.node2Id());
            }
            if (a.flowDirection() == FlowDirection.AGAINST_GEOMETRY || a.flowDirection() == FlowDirection.BOTH) {
                link(a.node2Id(), a.node1Id());
            }
        }
    }

    private void link(UUID from, UUID to) {
        downstreamAdj.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        upstreamAdj.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
    }

    public NetworkSnapshot snapshot() { return snapshot; }

    /** Nós a jusante do nó dado (inclusive ele), na ordem da BFS dirigida. */
    public List<UUID> downstreamNodes(UUID nodeId) {
        requireNode(nodeId);
        return bfs(nodeId, downstreamAdj);
    }

    /** Nós a montante do nó dado (inclusive ele), na ordem da BFS dirigida reversa. */
    public List<UUID> upstreamNodes(UUID nodeId) {
        requireNode(nodeId);
        return bfs(nodeId, upstreamAdj);
    }

    /** Nós a jusante de um arco: as duas pontas e tudo que elas alcançam a jusante. */
    public List<UUID> downstreamOfArc(UUID arcId) {
        NetworkArc a = requireArc(arcId);
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        out.addAll(bfs(a.node1Id(), downstreamAdj));
        out.addAll(bfs(a.node2Id(), downstreamAdj));
        return List.copyOf(out);
    }

    /** Nós a montante de um arco: as duas pontas e tudo que as alimenta. */
    public List<UUID> upstreamOfArc(UUID arcId) {
        NetworkArc a = requireArc(arcId);
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        out.addAll(bfs(a.node1Id(), upstreamAdj));
        out.addAll(bfs(a.node2Id(), upstreamAdj));
        return List.copyOf(out);
    }

    /** O nó {@code to} é alcançável a jusante de {@code from} pelo sentido de fluxo? */
    public boolean reachesDownstream(UUID fromNodeId, UUID toNodeId) {
        return downstreamNodes(fromNodeId).contains(toNodeId);
    }

    /** Adjacência NÃO dirigida (base do isolamento e dos setores). */
    public List<NetworkArc> arcsAt(UUID nodeId) {
        return List.copyOf(undirectedAdj.getOrDefault(nodeId, List.of()));
    }

    /** Componente conexo (não dirigido) contendo o nó, sem atravessar os nós barrados. */
    public Set<UUID> componentWithout(UUID startNodeId, Set<UUID> barredNodeIds) {
        requireNode(startNodeId);
        Set<UUID> seen = new LinkedHashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        if (barredNodeIds.contains(startNodeId)) return Set.of();
        seen.add(startNodeId);
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            for (NetworkArc a : undirectedAdj.getOrDefault(cur, List.of())) {
                UUID other = a.node1Id().equals(cur) ? a.node2Id() : a.node1Id();
                if (barredNodeIds.contains(other)) continue;
                if (seen.add(other)) queue.add(other);
            }
        }
        return Set.copyOf(seen);
    }

    private List<UUID> bfs(UUID start, Map<UUID, List<UUID>> adj) {
        List<UUID> order = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            order.add(cur);
            for (UUID next : adj.getOrDefault(cur, List.of())) {
                if (seen.add(next)) queue.add(next);
            }
        }
        return List.copyOf(order);
    }

    private void requireNode(UUID nodeId) {
        if (!snapshot.nodesById().containsKey(nodeId))
            throw new IllegalArgumentException("no fora do retrato: " + nodeId);
    }

    private NetworkArc requireArc(UUID arcId) {
        NetworkArc a = snapshot.arcsById().get(arcId);
        if (a == null) throw new IllegalArgumentException("arco fora do retrato: " + arcId);
        return a;
    }
}
