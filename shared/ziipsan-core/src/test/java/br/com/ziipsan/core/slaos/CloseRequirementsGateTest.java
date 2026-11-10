package br.com.ziipsan.core.slaos;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimativa II-4 §4.4 — encerramento sem exigencia => recusa tipificada; com tudo => encerra. */
class CloseRequirementsGateTest {

    private static final ProblemCauseRemedy.PcrCatalog CATALOG =
            new ProblemCauseRemedy.InMemoryPcrCatalog(Map.of(
                    "TST-P1", Map.of("TST-C1", Set.of("TST-R1"))));
    private static final ProblemCauseRemedy VALID_PCR =
            new ProblemCauseRemedy("TST-P1", "TST-C1", "TST-R1");
    private static final CloseGate.Requirements REQ =
            new CloseGate.Requirements(2, Set.of("TST-ITEM-A"), Set.of("TST-DOC-X"));
    private static final CloseGate.Evidence FULL =
            new CloseGate.Evidence(2, Set.of("TST-ITEM-A"), Set.of("TST-DOC-X"));

    private static List<CloseGate.RefusalKind> kinds(List<CloseGate.Refusal> refusals) {
        return refusals.stream().map(CloseGate.Refusal::kind).toList();
    }

    @Test
    void semFotoExigidaRecusaTipificada() {
        var ev = new CloseGate.Evidence(1, Set.of("TST-ITEM-A"), Set.of("TST-DOC-X"));
        var refusals = CloseGate.evaluate(ServiceOrderStatus.EXECUTED, REQ, ev, VALID_PCR, CATALOG);
        assertEquals(List.of(CloseGate.RefusalKind.MISSING_PHOTOS), kinds(refusals));
        var ex = assertThrows(CloseGate.CloseBlockedException.class, () ->
                CloseGate.requireClosable(ServiceOrderStatus.EXECUTED, REQ, ev, VALID_PCR, CATALOG));
        assertEquals(refusals, ex.refusals());
    }

    @Test
    void semChecklistEDocumentoRecusasTipificadas() {
        var ev = new CloseGate.Evidence(2, Set.of(), Set.of());
        var refusals = CloseGate.evaluate(ServiceOrderStatus.EXECUTED, REQ, ev, VALID_PCR, CATALOG);
        assertEquals(List.of(CloseGate.RefusalKind.MISSING_CHECKLIST_ITEM,
                CloseGate.RefusalKind.MISSING_DOCUMENT), kinds(refusals));
    }

    @Test
    void comTudoEncerra() {
        assertTrue(CloseGate.evaluate(ServiceOrderStatus.EXECUTED, REQ, FULL, VALID_PCR, CATALOG).isEmpty());
        assertDoesNotThrow(() ->
                CloseGate.requireClosable(ServiceOrderStatus.EXECUTED, REQ, FULL, VALID_PCR, CATALOG));
    }

    @Test
    void estadoNaoExecutadoRecusaAntesDasExigencias() {
        var refusals = CloseGate.evaluate(ServiceOrderStatus.OPEN, REQ, FULL, VALID_PCR, CATALOG);
        assertEquals(List.of(CloseGate.RefusalKind.ORDER_NOT_EXECUTED), kinds(refusals));
    }
}
