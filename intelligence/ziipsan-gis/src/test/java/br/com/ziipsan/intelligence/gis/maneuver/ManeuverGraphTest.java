package br.com.ziipsan.intelligence.gis.maneuver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Traçados montante/jusante (TR-1064) na massa sintética declarada. */
class ManeuverGraphTest {

    private final ManeuverGraph graph = new ManeuverGraph(base());

    @Test
    void downstreamFromSourceFollowsFlowDirectionInBfsOrder() {
        assertEquals(List.of(S, N1, VA, N2, VX, N3, VB, N6, N4, N5),
                graph.downstreamNodes(S));
    }

    @Test
    void upstreamFromEndReachesSourceWithoutBranch() {
        assertEquals(List.of(N5, N4, VB, N3, VX, N2, VA, N1, S),
                graph.upstreamNodes(N5));
    }

    @Test
    void unknownFlowDirectionCreatesNoDirectedEdge() {
        assertEquals(List.of(N5), graph.downstreamNodes(N5));
        assertFalse(graph.downstreamNodes(N5).contains(Z1));
        assertEquals(List.of(Z1), graph.upstreamNodes(Z1));
    }

    @Test
    void retiredArcIsOutOfTracing() {
        // A11 (N4-N5, RETIRED) não pode criar atalho: jusante de N4 é só N5 por A8.
        assertEquals(List.of(N4, N5), graph.downstreamNodes(N4));
    }

    @Test
    void arcTracingCombinesBothEnds() {
        assertTrue(graph.downstreamOfArc(A9).contains(N6));
        assertTrue(graph.upstreamOfArc(A9).contains(S));
        assertTrue(graph.reachesDownstream(N3, N5));
        assertFalse(graph.reachesDownstream(N5, N3));
    }

    @Test
    void unknownNodeOrArcRefused() {
        assertThrows(IllegalArgumentException.class, () -> graph.downstreamNodes(id(999)));
        assertThrows(IllegalArgumentException.class, () -> graph.upstreamOfArc(id(999)));
    }
}
