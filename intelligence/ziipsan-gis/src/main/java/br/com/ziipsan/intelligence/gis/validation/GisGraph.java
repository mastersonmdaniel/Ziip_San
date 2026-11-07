package br.com.ziipsan.intelligence.gis.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grafos puros da validação GIS (§5.6): componentes conexos por adjacência
 * de {@code node_1_id}/{@code node_2_id} e detecção de ciclo dirigido.
 *
 * <p>Sem banco e sem Spring: o {@link GisComponentService} carrega as
 * arestas, este código calcula. É a implementação Java cuja igualdade com
 * o {@code pgr_connectedComponents} o §5.7 e o aceite da GIS-03 pedem.
 */
public final class GisGraph {

    /** Aresta não dirigida entre dois nós, identificada pelo arco. */
    public record Edge(UUID arcId, UUID node1Id, UUID node2Id) {
    }

    /** Aresta dirigida (sentido do fluxo), identificada pelo arco. */
    public record DirectedEdge(UUID arcId, UUID fromNodeId, UUID toNodeId) {
    }

    private GisGraph() {
    }

    /**
     * Componentes conexos (union-find) dos nós dados as arestas.
     *
     * @return mapa nó → representante do componente (o menor UUID do
     * componente, para o resultado ser determinístico entre execuções)
     */
    public static Map<UUID, UUID> components(Collection<UUID> nodeIds, List<Edge> edges) {
        Map<UUID, UUID> parent = new HashMap<>();
        for (UUID node : nodeIds) {
            parent.put(node, node);
        }
        for (Edge edge : edges) {
            if (edge.node1Id() == null || edge.node2Id() == null) {
                continue;
            }
            parent.putIfAbsent(edge.node1Id(), edge.node1Id());
            parent.putIfAbsent(edge.node2Id(), edge.node2Id());
            union(parent, edge.node1Id(), edge.node2Id());
        }
        // Representante determinístico: o menor UUID de cada componente.
        Map<UUID, UUID> smallest = new HashMap<>();
        for (UUID node : parent.keySet()) {
            UUID root = find(parent, node);
            smallest.merge(root, node, (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        Map<UUID, UUID> result = new LinkedHashMap<>();
        for (UUID node : parent.keySet()) {
            result.put(node, smallest.get(find(parent, node)));
        }
        return result;
    }

    /** Agrupa os nós por representante de componente. */
    public static Map<UUID, List<UUID>> groups(Map<UUID, UUID> components) {
        Map<UUID, List<UUID>> groups = new LinkedHashMap<>();
        components.forEach((node, root) -> groups.computeIfAbsent(root, k -> new ArrayList<>()).add(node));
        return groups;
    }

    /**
     * Procura um ciclo dirigido (DFS com três cores).
     *
     * @return os ids dos arcos de um ciclo encontrado, na ordem do ciclo;
     * lista vazia quando o grafo é acíclico
     */
    public static List<UUID> findDirectedCycle(List<DirectedEdge> edges) {
        Map<UUID, List<DirectedEdge>> outgoing = new HashMap<>();
        Set<UUID> nodes = new HashSet<>();
        for (DirectedEdge edge : edges) {
            if (edge.fromNodeId() == null || edge.toNodeId() == null) {
                continue;
            }
            outgoing.computeIfAbsent(edge.fromNodeId(), k -> new ArrayList<>()).add(edge);
            nodes.add(edge.fromNodeId());
            nodes.add(edge.toNodeId());
        }
        Map<UUID, Integer> color = new HashMap<>(); // 0 branco, 1 cinza, 2 preto
        Deque<DirectedEdge> stack = new ArrayDeque<>();
        for (UUID start : nodes) {
            if (color.getOrDefault(start, 0) != 0) {
                continue;
            }
            List<UUID> cycle = visit(start, outgoing, color, stack);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<UUID> visit(UUID node, Map<UUID, List<DirectedEdge>> outgoing,
                                    Map<UUID, Integer> color, Deque<DirectedEdge> stack) {
        color.put(node, 1);
        for (DirectedEdge edge : outgoing.getOrDefault(node, List.of())) {
            UUID next = edge.toNodeId();
            int nextColor = color.getOrDefault(next, 0);
            if (nextColor == 0) {
                stack.push(edge);
                List<UUID> cycle = visit(next, outgoing, color, stack);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
                stack.pop();
            } else if (nextColor == 1) {
                // Volta a um nó da pilha atual: os arcos da pilha do nó
                // reencontrado até aqui, mais esta aresta, formam o ciclo.
                List<UUID> cycle = new ArrayList<>();
                cycle.add(edge.arcId());
                for (DirectedEdge stacked : stack) {
                    cycle.add(stacked.arcId());
                    if (stacked.fromNodeId().equals(next)) {
                        break;
                    }
                }
                return cycle;
            }
        }
        color.put(node, 2);
        return List.of();
    }

    private static UUID find(Map<UUID, UUID> parent, UUID node) {
        UUID root = node;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        UUID current = node;
        while (!parent.get(current).equals(root)) {
            UUID next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(Map<UUID, UUID> parent, UUID a, UUID b) {
        UUID rootA = find(parent, a);
        UUID rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootB, rootA);
        }
    }
}
