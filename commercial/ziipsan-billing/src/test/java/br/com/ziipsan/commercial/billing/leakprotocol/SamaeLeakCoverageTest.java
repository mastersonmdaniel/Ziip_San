package br.com.ziipsan.commercial.billing.leakprotocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamaeLeakCoverageTest {

    @Test
    void coversItems212And2125() {
        assertTrue(SamaeLeakCoverage.entries().stream()
                .anyMatch(e -> e.item().equals("2.12")));
        assertTrue(SamaeLeakCoverage.entries().stream()
                .anyMatch(e -> e.item().equals("2.12.5")));
        assertEquals(7, SamaeLeakCoverage.entries().size());
    }

    @Test
    void noSgCodeIsInvented() {
        for (SamaeLeakCoverage.Entry entry : SamaeLeakCoverage.entries()) {
            assertTrue(!entry.coveredBy().contains("SG-"),
                    "codigo SG nao pode ser inventado: " + entry);
            assertTrue(!entry.requirement().isBlank() && !entry.coveredBy().isBlank());
        }
    }
}
