package br.com.ziipsan.intelligence.gis.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GisGraph: componentes conexos e ciclo dirigido puros (§5.6), a base da
 * ilha sem fonte, do ciclo por gravidade e da comparação com o pgRouting.
 */
class GisGraphTest {

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    @Test
    @DisplayName("componentes: cadeia é um só componente, nó isolado é outro")
    void componentsGroupByAdjacency() {
        Map<UUID, UUID> components = GisGraph.components(
                Set.of(id(1), id(2), id(3), id(4)),
                List.of(new GisGraph.Edge(id(101), id(1), id(2)),
                        new GisGraph.Edge(id(102), id(2), id(3))));

        assertThat(components.get(id(1))).isEqualTo(components.get(id(3)));
        assertThat(components.get(id(4))).isNotEqualTo(components.get(id(1)));
        // Representante determinístico: o menor UUID do componente.
        assertThat(components.get(id(3))).isEqualTo(id(1));
    }

    @Test
    @DisplayName("componentes: arco sem ponta não liga nada")
    void edgeWithoutEndpointIsIgnored() {
        Map<UUID, UUID> components = GisGraph.components(
                Set.of(id(1), id(2)),
                List.of(new GisGraph.Edge(id(101), id(1), null)));

        assertThat(components.get(id(1))).isNotEqualTo(components.get(id(2)));
    }

    @Test
    @DisplayName("ciclo dirigido: anel de três arcos é detectado com os três arcos")
    void directedCycleIsDetected() {
        List<UUID> cycle = GisGraph.findDirectedCycle(List.of(
                new GisGraph.DirectedEdge(id(101), id(1), id(2)),
                new GisGraph.DirectedEdge(id(102), id(2), id(3)),
                new GisGraph.DirectedEdge(id(103), id(3), id(1)),
                new GisGraph.DirectedEdge(id(104), id(3), id(4))));

        assertThat(cycle).containsExactlyInAnyOrder(id(101), id(102), id(103));
    }

    @Test
    @DisplayName("ciclo dirigido: DAG por gravidade não tem ciclo")
    void dagHasNoCycle() {
        assertThat(GisGraph.findDirectedCycle(List.of(
                new GisGraph.DirectedEdge(id(101), id(1), id(2)),
                new GisGraph.DirectedEdge(id(102), id(1), id(3)),
                new GisGraph.DirectedEdge(id(103), id(2), id(4)),
                new GisGraph.DirectedEdge(id(104), id(3), id(4))))).isEmpty();
    }

    @Test
    @DisplayName("ciclo dirigido: laço de um arco só é ciclo")
    void selfLoopIsCycle() {
        assertThat(GisGraph.findDirectedCycle(List.of(
                new GisGraph.DirectedEdge(id(101), id(1), id(1))))).containsExactly(id(101));
    }
}
