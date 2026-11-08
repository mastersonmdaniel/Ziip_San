package br.com.ziipsan.intelligence.gis.waste;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Grafo de resultado conhecido (GIS-14, §5.7): linha 1-2-3-4-5 com
 * atalhos caros e uma mão única 5->1 barata.
 *   1 -100- 2 -100- 3 -100- 4 -100- 5
 *   1 -------500------- 3
 *   2 -----------1000---------- 5
 *   5 -------100 (só ida)------> 1
 */
class GisWasteRoutePlannerTest {

    private static List<GisWasteRoutePlanner.StreetEdge> graph() {
        return List.of(
                new GisWasteRoutePlanner.StreetEdge(1, 1, 2, 100, 100),
                new GisWasteRoutePlanner.StreetEdge(2, 2, 3, 100, 100),
                new GisWasteRoutePlanner.StreetEdge(3, 1, 3, 500, 500),
                new GisWasteRoutePlanner.StreetEdge(4, 3, 4, 100, 100),
                new GisWasteRoutePlanner.StreetEdge(5, 4, 5, 100, 100),
                new GisWasteRoutePlanner.StreetEdge(6, 2, 5, 1000, 1000),
                new GisWasteRoutePlanner.StreetEdge(7, 5, 1, 100, -1));
    }

    @Test
    void shortestPathPicksTheCheapLineNotTheShortcut() {
        GisWasteRoutePlanner.Path path = GisWasteRoutePlanner.shortestPath(graph(), 1, 3);
        assertNotNull(path);
        assertEquals(200.0, path.totalCostM(), 1e-9);
        assertEquals(List.of(1L, 2L, 3L), path.vertices());
        GisWasteRoutePlanner.Path toFive = GisWasteRoutePlanner.shortestPath(graph(), 1, 5);
        assertNotNull(toFive);
        assertEquals(400.0, toFive.totalCostM(), 1e-9);
    }

    @Test
    void oneWayEdgeIsNotTraversedBackwards() {
        // 5->1 custa 100 pela mao unica; 1->5 NAO pode usa-la ao contrario.
        GisWasteRoutePlanner.Path back = GisWasteRoutePlanner.shortestPath(graph(), 5, 1);
        assertNotNull(back);
        assertEquals(100.0, back.totalCostM(), 1e-9);
        assertEquals(List.of(7L), back.edgeIds());
        GisWasteRoutePlanner.Path forth = GisWasteRoutePlanner.shortestPath(graph(), 1, 5);
        assertNotNull(forth);
        assertEquals(List.of(1L, 2L, 4L, 5L), forth.edgeIds());
    }

    @Test
    void unreachableVertexReturnsNull() {
        assertNull(GisWasteRoutePlanner.shortestPath(graph(), 1, 99));
    }

    @Test
    void sequenceVisitsStopsInLineOrderWithKnownTotals() {
        double[][] matrix = GisWasteRoutePlanner.distanceMatrix(graph(),
                List.of(1L, 2L, 3L, 4L, 5L));
        List<Integer> order = GisWasteRoutePlanner.sequenceStops(matrix);
        assertEquals(List.of(1, 2, 3, 4), order);
        assertEquals(400.0, GisWasteRoutePlanner.tourCost(matrix, order), 1e-9);
        assertEquals(100.0, GisWasteRoutePlanner.returnCost(matrix, order), 1e-9);
    }

    @Test
    void sequenceRejectsUnreachableStop() {
        double[][] matrix = GisWasteRoutePlanner.distanceMatrix(graph(),
                List.of(1L, 2L, 99L));
        assertThrows(IllegalArgumentException.class,
                () -> GisWasteRoutePlanner.sequenceStops(matrix));
    }

    @Test
    void twoOptImprovesNearestNeighbour() {
        // Dirigido: o vizinho mais proximo sai do deposito para 1 (custo
        // 1) e fica preso em trechos de 100 — NN da [1,2,3] com 102.
        // A reversao completa [3,2,1] custa 2+1+1 = 4: o 2-opt a aceita.
        double[][] m = {
                {0, 1, 2, 2},
                {1, 0, 100, 100},
                {2, 1, 0, 1},
                {2, 1, 1, 0}};
        List<Integer> order = GisWasteRoutePlanner.sequenceStops(m);
        assertEquals(List.of(3, 2, 1), order);
        assertEquals(4.0, GisWasteRoutePlanner.tourCost(m, order), 1e-9);
    }
}
