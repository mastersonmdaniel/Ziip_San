package br.com.ziipsan.core.sewerraw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReuseGateTest {

    @Test void notCodifiedBeforePublicationConfirmation() {
        var r = ReuseGate.evaluate(new ReuseGate.ReuseNormStatus(false, null));
        assertEquals(ReuseGate.ReuseStatus.NOT_CODIFIED, r.status());
        assertTrue(r.references().stream().anyMatch(s -> s.contains("54/2005")));
        assertTrue(r.references().stream().anyMatch(s -> s.contains("121/2010")));
        assertThrows(IllegalStateException.class, () ->
                ReuseGate.requireCodified(new ReuseGate.ReuseNormStatus(false, null), "uso declarado fixture"));
    }

    @Test void codifiedBasisOnlyWithConfirmedPublicationAndReference() {
        assertThrows(IllegalArgumentException.class, () -> new ReuseGate.ReuseNormStatus(true, " "));
        var r = ReuseGate.evaluate(new ReuseGate.ReuseNormStatus(true, "RESOLUCAO-FIXTURE [FIXTURE]"));
        assertEquals(ReuseGate.ReuseStatus.CODIFIED_BASIS_CONFIRMED, r.status());
        ReuseGate.requireCodified(new ReuseGate.ReuseNormStatus(true, "RESOLUCAO-FIXTURE [FIXTURE]"), "uso declarado fixture");
    }
}
