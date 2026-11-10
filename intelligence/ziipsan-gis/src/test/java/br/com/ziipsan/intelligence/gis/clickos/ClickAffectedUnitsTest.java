package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickOutcome;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.ClickPropagationConfig;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationMode;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsModel.PropagationResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aceite §4.2/§4.3 da estimativa II-1: UCs afectadas = conjunto
 * exacto das ligações dos elementos afectados, sem duplicados;
 * elemento sem UCs / isolado tem desfecho tipificado.
 */
class ClickAffectedUnitsTest {

    @Test
    void sharedUnitAcrossTwoConnectionsAppearsOnce() {
        PropagationResult r = ClickPropagationEngine.propagate(ClickOsFixtures.withSharedUnit(),
                Target.node(N2), PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        // C2 sensível, C3 essencial, C1/C4/C9 por código; U1 chega por C1 e por C9 — uma vez.
        assertEquals(List.of(U2, U3, U4, U1, U5), r.affectedUnitIds());
        assertEquals(r.affectedUnitIds().size(),
                r.affectedUnitIds().stream().distinct().count());
        assertEquals(5, r.affectedConnections().size());
    }

    @Test
    void isolatedElementHasTypedOutcome() {
        // Z1: o único arco (A10) tem sentido UNKNOWN — nenhuma aresta dirigida o liga.
        PropagationResult r = ClickPropagationEngine.propagate(base(),
                Target.node(Z1), PropagationMode.DOWNSTREAM, ClickPropagationConfig.proofDefaults());
        assertEquals(List.of(Z1), r.affectedNodeIds());
        assertEquals(List.of(), r.affectedUnitIds());
        assertEquals(ClickOutcome.ISOLATED_ELEMENT, r.outcome());
    }

    @Test
    void elementWithoutUnitsHasTypedOutcome() {
        PropagationResult r = ClickPropagationEngine.propagate(ClickOsFixtures.withoutConnections(),
                Target.node(ClickOsFixtures.id(701)), PropagationMode.DOWNSTREAM,
                ClickPropagationConfig.proofDefaults());
        assertEquals(List.of(ClickOsFixtures.id(701), ClickOsFixtures.id(702)), r.affectedNodeIds());
        assertTrue(r.affectedUnitIds().isEmpty());
        assertEquals(ClickOutcome.WITHOUT_AFFECTED_UNITS, r.outcome());
    }

    @Test
    void notIsolableHasTypedOutcomeAndReason() {
        // A1 nasce na fonte S: sem válvula operável entre a fonte e o alvo, não isola.
        PropagationResult r = ClickPropagationEngine.propagate(base(),
                Target.arc(A1), PropagationMode.ISOLATION, ClickPropagationConfig.proofDefaults());
        assertEquals(ClickOutcome.NOT_ISOLABLE, r.outcome());
        assertEquals("SOURCE_REACHED_WITHOUT_VALVE", r.isolationFailure());
        assertTrue(r.affectedUnitIds().isEmpty());
    }
}
