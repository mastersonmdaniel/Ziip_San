package br.com.ziipsan.intelligence.gis.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GisComponentConsistencyChecker: a prova "pgr igual ao traçado Java"
 * (§5.7) com grafos sintéticos de resultado conhecido — sem banco, o
 * pgRouting entra como mapa nó → componente.
 */
class GisComponentConsistencyCheckerTest {

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    /** Rede sintética: fonte 1 — 2 — 3 num componente; 4 — 5 em outro. */
    private static Map<UUID, UUID> javaComponents() {
        return GisGraph.components(Set.of(id(1), id(2), id(3), id(4), id(5)),
                List.of(new GisGraph.Edge(id(101), id(1), id(2)),
                        new GisGraph.Edge(id(102), id(2), id(3)),
                        new GisGraph.Edge(id(103), id(4), id(5))));
    }

    @Test
    @DisplayName("partições iguais não divergem, mesmo com ids de componente diferentes")
    void equalPartitionsHaveNoDivergence() {
        Map<UUID, Long> pgr = Map.of(
                id(1), 7L, id(2), 7L, id(3), 7L, id(4), 9L, id(5), 9L);

        assertThat(GisComponentConsistencyChecker.compare(javaComponents(), pgr)).isEmpty();
    }

    @Test
    @DisplayName("pgRouting partindo um componente Java é divergência nos dois sentidos")
    void splitComponentDiverges() {
        Map<UUID, Long> pgr = Map.of(
                id(1), 7L, id(2), 7L, id(3), 8L, id(4), 9L, id(5), 9L);

        assertThat(GisComponentConsistencyChecker.compare(javaComponents(), pgr))
                .extracting(GisComponentConsistencyChecker.Divergence::kind)
                .contains(GisComponentConsistencyChecker.JAVA_SPLIT_IN_PGR,
                        GisComponentConsistencyChecker.PGR_SPLIT_IN_JAVA);
    }

    @Test
    @DisplayName("nó só de um lado é divergência nomeada")
    void nodeOnlyOnOneSideDiverges() {
        Map<UUID, Long> pgr = Map.of(id(1), 7L, id(2), 7L, id(3), 7L, id(4), 9L);

        assertThat(GisComponentConsistencyChecker.compare(javaComponents(), pgr))
                .extracting(GisComponentConsistencyChecker.Divergence::kind)
                .containsExactly(GisComponentConsistencyChecker.ONLY_IN_JAVA);
    }
}
