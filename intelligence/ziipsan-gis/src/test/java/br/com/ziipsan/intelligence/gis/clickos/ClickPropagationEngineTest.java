package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickOutcome;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickPropagationConfig;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverGraph;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aceite §4.1/§4.2 da estimativa II-1 sobre a rede fixture
 * determinística da GIS-11: conjuntos exactos de elementos e de
 * UCs afectados, sem duplicados.
 */
class ClickPropagationEngineTest {

    private PropagationResult propagate(Target t, PropagationMode m) {
        return ClickPropagationEngine.propagate(base(), t, m, ClickPropagationConfig.proofDefaults());
    }

    @Test
    void downstreamFromNodeExactSetAndUnits() {
        PropagationResult r = propagate(Target.node(N3), PropagationMode.DOWNSTREAM);
        assertEquals(List.of(N3, VB, N6, N4, N5), r.affectedNodeIds());
        assertEquals(List.of(A6, A7, A8, A9), r.affectedArcIds());
        // Ordem do aviso (GIS-11): sensível C2, essencial C3, depois C4 por código.
        assertEquals(List.of(U2, U3, U4, U5), r.affectedUnitIds());
        assertEquals(8, r.totalEconomies());
        assertEquals(List.of(U4), r.essentialUnitIds());
        assertEquals(List.of(U2, U3), r.sensitiveUnitIds());
        assertEquals(ClickOutcome.WITH_AFFECTED_UNITS, r.outcome());
        assertFalse(r.truncated());
    }

    @Test
    void upstreamFromNodeExactSetAndUnits() {
        PropagationResult r = propagate(Target.node(N5), PropagationMode.UPSTREAM);
        assertEquals(List.of(N5, N4, VB, N3, VX, N2, VA, N1, S), r.affectedNodeIds());
        assertEquals(List.of(A1, A2, A3, A4, A5, A6, A7, A8), r.affectedArcIds());
        assertEquals(List.of(U2, U3, U1, U5), r.affectedUnitIds());
        assertEquals(9, r.totalEconomies());
    }

    @Test
    void arcClickDownstreamCombinesBothEnds() {
        PropagationResult r = propagate(Target.arc(A9), PropagationMode.DOWNSTREAM);
        assertEquals(List.of(N3, VB, N6, N4, N5), r.affectedNodeIds());
        assertEquals(List.of(U2, U3, U4, U5), r.affectedUnitIds());
    }

    @Test
    void bothMergesDownstreamThenUpstreamWithoutDuplicates() {
        PropagationResult r = propagate(Target.node(N3), PropagationMode.BOTH);
        assertEquals(List.of(N3, VB, N6, N4, N5, VX, N2, VA, N1, S), r.affectedNodeIds());
        assertEquals(List.of(A1, A2, A3, A4, A5, A6, A7, A8, A9), r.affectedArcIds());
        assertEquals(List.of(U2, U3, U4, U1, U5), r.affectedUnitIds());
    }

    @Test
    void unlimitedTraceEqualsManeuverGraphOfGis11() {
        ManeuverGraph graph = new ManeuverGraph(base());
        assertEquals(graph.downstreamNodes(N3),
                propagate(Target.node(N3), PropagationMode.DOWNSTREAM).affectedNodeIds());
        assertEquals(graph.upstreamNodes(N5),
                propagate(Target.node(N5), PropagationMode.UPSTREAM).affectedNodeIds());
        assertEquals(graph.downstreamOfArc(A9),
                propagate(Target.arc(A9), PropagationMode.DOWNSTREAM).affectedNodeIds());
    }

    @Test
    void isolationModeDelegatesToIsolationEngine() {
        PropagationResult r = propagate(Target.arc(A5), PropagationMode.ISOLATION);
        assertEquals(ClickOutcome.WITH_AFFECTED_UNITS, r.outcome());
        // Área conhecida da GIS-11 para A5 (ordenada por código de nó).
        assertEquals(List.of(N2, N3, N6, VX), r.affectedNodeIds());
        assertEquals(List.of(A4, A5, A9), r.affectedArcIds());
        assertEquals(List.of(U2, U3, U4, U1), r.affectedUnitIds());
        assertEquals(List.of("VA", "VB"), r.valvesToCloseCodes());
    }

    @Test
    void maxHopsTruncatesAndDeclaresIt() {
        PropagationResult r = ClickPropagationEngine.propagate(base(), Target.node(S),
                PropagationMode.DOWNSTREAM, new ClickPropagationConfig(1, null));
        assertEquals(List.of(S, N1), r.affectedNodeIds());
        assertTrue(r.truncated());
        assertFalse(r.unitLimitExceeded());
        assertEquals(ClickOutcome.WITHOUT_AFFECTED_UNITS, r.outcome());
    }

    @Test
    void unitLimitExceededIsDeclaredAndUnitsStayWhole() {
        PropagationResult r = ClickPropagationEngine.propagate(base(), Target.node(N3),
                PropagationMode.DOWNSTREAM, new ClickPropagationConfig(null, 3));
        assertTrue(r.truncated());
        assertTrue(r.unitLimitExceeded());
        // A lista NÃO é cortada em silêncio: vem inteira, com a truncagem declarada.
        assertEquals(List.of(U2, U3, U4, U5), r.affectedUnitIds());
    }

    @Test
    void unknownElementIsRefusedNotSilenced() {
        assertThrows(IllegalArgumentException.class,
                () -> propagate(Target.node(id(999)), PropagationMode.DOWNSTREAM));
        assertThrows(IllegalArgumentException.class,
                () -> propagate(Target.arc(id(999)), PropagationMode.UPSTREAM));
    }
}
