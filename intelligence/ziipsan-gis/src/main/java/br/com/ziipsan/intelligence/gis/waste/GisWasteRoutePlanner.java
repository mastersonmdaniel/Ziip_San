package br.com.ziipsan.intelligence.gis.waste;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Sequenciamento de rota de coleta sobre o grafo de logradouros
 * (GIS-14; §5.7: pgRouting é "para lote e grafo de ruas — rotas de
 * coleta sobre logradouros (pgr_TSP para pontos...)").
 *
 * <p>Divisão de trabalho, declarada: no banco, o SQL pendente da
 * ED-65 cria o grafo ({@code gis_waste_street_edge} com
 * source/target/cost) e a função que chama {@code pgr_dijkstra}/
 * {@code pgr_TSP} quando a extensão pgRouting está presente. Esta
 * classe é a lógica de sequenciamento TESTÁVEL sem banco e o
 * espelho do resultado esperado: dado o grafo (arestas com custo em
 * metros, mão única quando {@code reverseCostM < 0}), calcula o
 * menor caminho entre dois vértices (Dijkstra) e, dada a matriz de
 * distâncias entre depósito e paradas, ordena as paradas por
 * vizinho-mais-próximo com melhoria 2-opt determinística — a mesma
 * heurística que o serviço aplica sobre a matriz que o pgRouting
 * devolve. Divergência entre esta classe e o pgr_TSP em massa
 * sintética é item de ensaio da integração (o §5.7 manda validar a
 * escolha na GIS-14; o chinês carteiro/pgr_chinesePostman, para
 * PERCORRER vias — varrição —, segue experimental e fora desta
 * entrega, declarado no relatório).
 *
 * <p>Determinismo: desempates sempre pelo menor id de vértice e pela
 * menor posição na lista, para o mesmo grafo dar sempre a mesma rota
 * (teste com resultado conhecido).
 */
public final class GisWasteRoutePlanner {

    /** Aresta do grafo de logradouros: custo em metros por sentido. */
    public record StreetEdge(long id, long source, long target,
                             double costM, double reverseCostM) {
        public StreetEdge {
            if (costM < 0) {
                throw new IllegalArgumentException("custo da aresta nao pode ser negativo");
            }
        }

        /** Mão única: sem custo de retorno, não se volta por ela. */
        public boolean oneWay() {
            return reverseCostM < 0;
        }
    }

    /** Resultado de um menor caminho: vértices, arestas e custo total. */
    public record Path(List<Long> vertices, List<Long> edgeIds, double totalCostM) {
    }

    private GisWasteRoutePlanner() {
    }

    /**
     * Menor caminho (Dijkstra) de {@code from} a {@code to} no grafo.
     * Sem caminho, devolve null (o chamador trata: parada inalcançável
     * é problema de grafo a reportar, nunca custo zero presumido).
     */
    public static Path shortestPath(List<StreetEdge> edges, long from, long to) {
        if (from == to) {
            return new Path(List.of(from), List.of(), 0.0);
        }
        Map<Long, List<Step>> adjacency = new HashMap<>();
        for (StreetEdge e : edges) {
            adjacency.computeIfAbsent(e.source(), k -> new ArrayList<>())
                    .add(new Step(e.target(), e.id(), e.costM()));
            if (!e.oneWay()) {
                adjacency.computeIfAbsent(e.target(), k -> new ArrayList<>())
                        .add(new Step(e.source(), e.id(), e.reverseCostM()));
            }
        }
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prevVertex = new HashMap<>();
        Map<Long, Long> prevEdge = new HashMap<>();
        PriorityQueue<long[]> queue = new PriorityQueue<>(
                Comparator.comparingDouble(a -> Double.longBitsToDouble(a[1])));
        Set<Long> settled = new HashSet<>();
        dist.put(from, 0.0);
        queue.add(new long[]{from, Double.doubleToLongBits(0.0)});
        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            long vertex = head[0];
            if (!settled.add(vertex)) {
                continue;
            }
            if (vertex == to) {
                break;
            }
            double base = dist.getOrDefault(vertex, Double.POSITIVE_INFINITY);
            for (Step step : adjacency.getOrDefault(vertex, List.of())) {
                double candidate = base + step.costM();
                if (candidate < dist.getOrDefault(step.to(), Double.POSITIVE_INFINITY)) {
                    dist.put(step.to(), candidate);
                    prevVertex.put(step.to(), vertex);
                    prevEdge.put(step.to(), step.edgeId());
                    queue.add(new long[]{step.to(), Double.doubleToLongBits(candidate)});
                }
            }
        }
        if (!dist.containsKey(to)) {
            return null;
        }
        List<Long> vertices = new ArrayList<>();
        List<Long> edgeIds = new ArrayList<>();
        long cursor = to;
        vertices.add(cursor);
        while (cursor != from) {
            edgeIds.add(prevEdge.get(cursor));
            cursor = prevVertex.get(cursor);
            vertices.add(cursor);
        }
        java.util.Collections.reverse(vertices);
        java.util.Collections.reverse(edgeIds);
        return new Path(List.copyOf(vertices), List.copyOf(edgeIds), dist.get(to));
    }

    private record Step(long to, long edgeId, double costM) {
    }

    /**
     * Matriz completa de menores caminhos entre os vértices dados
     * (depósito + paradas projetadas no vértice mais próximo — a
     * projeção é do serviço, via ST_ClosestPoint no banco; aqui os
     * vértices já vêm resolvidos). Entrada inalcançável vira
     * {@link Double#POSITIVE_INFINITY} e é recusada por
     * {@link #sequenceStops}: rota com parada inalcançável não nasce.
     */
    public static double[][] distanceMatrix(List<StreetEdge> edges, List<Long> vertices) {
        int n = vertices.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 0.0;
                    continue;
                }
                Path path = shortestPath(edges, vertices.get(i), vertices.get(j));
                matrix[i][j] = path == null ? Double.POSITIVE_INFINITY : path.totalCostM();
            }
        }
        return matrix;
    }

    /**
     * Ordena as paradas: índice 0 da matriz é o DEPÓSITO (garagem/
     * transbordo); os demais são as paradas na ordem de entrada.
     * Vizinho mais próximo a partir do depósito + 2-opt (sem retorno
     * ao depósito no meio; o retorno fecha a rota no chamador).
     * Devolve os ÍNDICES das paradas (1..n-1 da matriz) na ordem de
     * visita. Matriz assimétrica é aceita (mão única): o 2-opt usa o
     * custo dirigido e só aceita troca que reduz o total dirigido.
     */
    public static List<Integer> sequenceStops(double[][] matrix) {
        int n = matrix.length;
        if (n <= 1) {
            return List.of();
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && Double.isInfinite(matrix[i][j])) {
                    throw new IllegalArgumentException(
                            "parada inalcancavel no grafo de logradouros: " + i + " -> " + j);
                }
            }
        }
        // Vizinho mais próximo (desempate: menor índice).
        List<Integer> order = new ArrayList<>();
        Set<Integer> remaining = new HashSet<>();
        for (int i = 1; i < n; i++) {
            remaining.add(i);
        }
        int current = 0;
        while (!remaining.isEmpty()) {
            int best = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int candidate : remaining) {
                double cost = matrix[current][candidate];
                if (cost < bestCost || (cost == bestCost && candidate < best)) {
                    best = candidate;
                    bestCost = cost;
                }
            }
            order.add(best);
            remaining.remove(best);
            current = best;
        }
        // 2-opt dirigido: reverter um trecho muda o custo interno em
        // grafos assimétricos; só aceita se o total dirigido cai.
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < order.size() - 1 && !improved; i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    double before = tourCost(matrix, order);
                    List<Integer> candidate = new ArrayList<>(order);
                    java.util.Collections.reverse(candidate.subList(i, j + 1));
                    if (tourCost(matrix, candidate) < before - 1e-9) {
                        order = candidate;
                        improved = true;
                        break;
                    }
                }
            }
        }
        return List.copyOf(order);
    }

    /** Custo dirigido do tour depósito -> paradas (sem retorno). */
    public static double tourCost(double[][] matrix, List<Integer> order) {
        double total = 0.0;
        int current = 0;
        for (int stop : order) {
            total += matrix[current][stop];
            current = stop;
        }
        return total;
    }

    /** Custo do retorno da última parada ao depósito (fecha a rota). */
    public static double returnCost(double[][] matrix, List<Integer> order) {
        if (order.isEmpty()) {
            return 0.0;
        }
        return matrix[order.get(order.size() - 1)][0];
    }
}
