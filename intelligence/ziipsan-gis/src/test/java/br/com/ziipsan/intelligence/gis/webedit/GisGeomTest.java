package br.com.ziipsan.intelligence.gis.webedit;

import br.com.ziipsan.intelligence.gis.webedit.GisGeom.Pt;
import br.com.ziipsan.intelligence.gis.webedit.GisGeom.SplitResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GisGeomTest {

    private static final double EPS = 1e-6;

    private static List<Pt> line(double... xy) {
        java.util.List<Pt> out = new java.util.ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            out.add(new Pt(xy[i], xy[i + 1]));
        }
        return out;
    }

    @Test
    void wktRoundTripLineAndPolygon() {
        List<Pt> l = GisGeom.parseLine("LINESTRING(0 0, 10 0, 10 10)");
        assertEquals(3, l.size());
        assertEquals(l, GisGeom.parseLine(GisGeom.toLineWkt(l)));
        List<Pt> ring = GisGeom.parsePolygonRing(
                "POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))");
        assertEquals(5, ring.size());
        assertEquals(ring, GisGeom.parsePolygonRing(GisGeom.toPolygonWkt(ring)));
    }

    @Test
    void translateMovesEveryVertex() {
        List<Pt> out = GisGeom.translate(line(0, 0, 10, 0), 5, -2);
        assertEquals(new Pt(5, -2), out.get(0));
        assertEquals(new Pt(15, -2), out.get(1));
    }

    @Test
    void rotateNinetyDegreesAroundOrigin() {
        List<Pt> out = GisGeom.rotate(line(10, 0, 10, 10), new Pt(0, 0), 90);
        assertEquals(0, out.get(0).x(), EPS);
        assertEquals(10, out.get(0).y(), EPS);
        assertEquals(-10, out.get(1).x(), EPS);
        assertEquals(10, out.get(1).y(), EPS);
    }

    @Test
    void rotateFullTurnReturnsToStart() {
        List<Pt> shape = line(3, 4, 9, 4);
        List<Pt> out = GisGeom.rotate(shape, GisGeom.centroid(shape), 360);
        for (int i = 0; i < shape.size(); i++) {
            assertEquals(shape.get(i).x(), out.get(i).x(), 1e-9);
            assertEquals(shape.get(i).y(), out.get(i).y(), 1e-9);
        }
    }

    @Test
    void scaleDoublesDistancesFromPivotAndRejectsNonPositive() {
        List<Pt> out = GisGeom.scale(line(10, 0, 20, 0), new Pt(0, 0), 2);
        assertEquals(new Pt(20, 0), out.get(0));
        assertEquals(new Pt(40, 0), out.get(1));
        assertThrows(IllegalArgumentException.class,
                () -> GisGeom.scale(line(0, 0, 1, 1), new Pt(0, 0), 0));
    }

    @Test
    void moveVertexChangesOnlyThatVertex() {
        List<Pt> out = GisGeom.moveVertex(line(0, 0, 10, 0, 10, 10), 1,
                new Pt(12, 2));
        assertEquals(new Pt(0, 0), out.get(0));
        assertEquals(new Pt(12, 2), out.get(1));
        assertEquals(new Pt(10, 10), out.get(2));
    }

    @Test
    void splitAtMiddleProducesTwoArcsSharingTheNode() {
        SplitResult r = GisGeom.splitLineAt(line(0, 0, 10, 0), new Pt(4, 0), 0.01);
        assertEquals(new Pt(4, 0), r.node());
        assertEquals(r.node(), r.first().get(r.first().size() - 1));
        assertEquals(r.node(), r.second().get(0));
        assertEquals(10, GisGeom.length(r.first()) + GisGeom.length(r.second()),
                EPS);
    }

    @Test
    void splitProjectsPointOntoSegmentWithinTolerance() {
        SplitResult r = GisGeom.splitLineAt(line(0, 0, 10, 0),
                new Pt(5, 0.4), 0.5);
        assertEquals(new Pt(5, 0), r.node());
    }

    @Test
    void splitAtStartEndpointIsRefused() {
        // Correção D6 (revisão ED-53): na extremidade inicial o
        // primeiro trecho teria comprimento 0 (arco degenerado).
        assertThrows(IllegalArgumentException.class, () ->
                GisGeom.splitLineAt(line(0, 0, 10, 0), new Pt(0, 0), 0.01));
    }

    @Test
    void splitAtEndEndpointIsRefusedAndInsertNodeToo() {
        assertThrows(IllegalArgumentException.class, () ->
                GisGeom.splitLineAt(line(0, 0, 10, 0), new Pt(10, 0), 0.01));
        assertThrows(IllegalArgumentException.class, () ->
                GisGeom.insertNodeAt(line(0, 0, 10, 0, 10, 10),
                        new Pt(0, 0), 0.01));
    }

    @Test
    void splitRejectsPointAwayFromArc() {
        assertThrows(IllegalArgumentException.class, () ->
                GisGeom.splitLineAt(line(0, 0, 10, 0), new Pt(5, 9), 0.01));
    }

    @Test
    void insertNodeSegmentsAtExistingVertex() {
        SplitResult r = GisGeom.insertNodeAt(line(0, 0, 10, 0, 10, 10),
                new Pt(10, 0), 0.01);
        assertEquals(2, r.first().size());
        assertEquals(2, r.second().size());
        assertEquals(new Pt(10, 0), r.node());
    }

    @Test
    void mergeJoinsArcsAtSharedEndpointInEitherOrder() {
        List<Pt> merged = GisGeom.mergeLines(
                line(0, 0, 10, 0), line(20, 0, 10, 0), 0.01);
        assertEquals(line(0, 0, 10, 0, 20, 0), merged);
        List<Pt> merged2 = GisGeom.mergeLines(
                line(10, 0, 20, 0), line(0, 0, 10, 0), 0.01);
        assertEquals(line(0, 0, 10, 0, 20, 0), merged2);
    }

    @Test
    void mergeRejectsDisconnectedArcs() {
        assertThrows(IllegalArgumentException.class, () ->
                GisGeom.mergeLines(line(0, 0, 1, 0), line(5, 5, 6, 5), 0.01));
    }

    @Test
    void invalidGeometriesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GisGeom.parseLine("LINESTRING(0 0)"));
        assertThrows(IllegalArgumentException.class, () ->
                GisGeom.parsePolygonRing("POLYGON((0 0, 1 0, 1 1))"));
        assertTrue(GisGeom.length(line(0, 0, 3, 4)) == 5.0);
    }
}
