package br.com.ziipsan.core.slaos;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimativa II-4 §4.5 — sem codificacao => recusa; com codificacao valida (catalogo injectado) => encerra. */
class ProblemCauseRemedyGateTest {

    private static final ProblemCauseRemedy.PcrCatalog CATALOG =
            new ProblemCauseRemedy.InMemoryPcrCatalog(Map.of(
                    "TST-P1", Map.of("TST-C1", Set.of("TST-R1", "TST-R2"),
                                     "TST-C2", Set.of("TST-R3"))));
    private static final CloseGate.Requirements NO_REQ = CloseGate.Requirements.none();
    private static final CloseGate.Evidence NO_EV = new CloseGate.Evidence(0, Set.of(), Set.of());

    @Test
    void semCodificacaoRecusa() {
        var refusals = CloseGate.evaluate(ServiceOrderStatus.EXECUTED, NO_REQ, NO_EV, null, CATALOG);
        assertEquals(List.of(CloseGate.RefusalKind.MISSING_PROBLEM_CAUSE_REMEDY),
                refusals.stream().map(CloseGate.Refusal::kind).toList());
    }

    @Test
    void problemaDesconhecidoRecusaInvalida() {
        var pcr = new ProblemCauseRemedy("TST-XX", "TST-C1", "TST-R1");
        var refusals = CloseGate.evaluate(ServiceOrderStatus.EXECUTED, NO_REQ, NO_EV, pcr, CATALOG);
        assertEquals(1, refusals.size());
        assertEquals(CloseGate.RefusalKind.INVALID_PROBLEM_CAUSE_REMEDY, refusals.get(0).kind());
        assertEquals("UNKNOWN_PROBLEM", refusals.get(0).detail());
    }

    @Test
    void combinacaoInvalidaRecusaInvalida() {
        var causaErrada = new ProblemCauseRemedy("TST-P1", "TST-C2", "TST-R1");
        var r1 = CloseGate.evaluate(ServiceOrderStatus.EXECUTED, NO_REQ, NO_EV, causaErrada, CATALOG);
        assertEquals("REMEDY_NOT_FOR_COMBINATION", r1.get(0).detail());
        var remedioDeOutraCausa = new ProblemCauseRemedy("TST-P1", "TST-C9", "TST-R1");
        var r2 = CloseGate.evaluate(ServiceOrderStatus.EXECUTED, NO_REQ, NO_EV, remedioDeOutraCausa, CATALOG);
        assertEquals("CAUSE_NOT_FOR_PROBLEM", r2.get(0).detail());
    }

    @Test
    void codificacaoValidaEncerra() {
        var pcr = new ProblemCauseRemedy("TST-P1", "TST-C2", "TST-R3");
        assertTrue(CloseGate.evaluate(ServiceOrderStatus.EXECUTED, NO_REQ, NO_EV, pcr, CATALOG).isEmpty());
    }
}
