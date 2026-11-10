package br.com.ziipsan.commercial.collection.cession;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ED-103/E5 — reconciliacao conta vinculada: casamento exacto e divergencias tipificadas. */
class ReconciliationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    /** Cede R1 (750), R2 (500) e R4 (200) e devolve o registo. */
    private CessionRegistry cededRegistry() {
        var r = new CessionRegistry(new InMemoryCessionStore());
        for (var e : List.of(new String[][]{{"R1", "750.00"}, {"R2", "500.00"}, {"R4", "200.00"}})) {
            r.reserve(CessionFixtures.TENANT, e[0], "dev-hash-x", new BigDecimal(e[1]),
                    "CONTA-VINCULADA-DECLARADA", "op", T0);
            r.cede(CessionFixtures.TENANT, e[0], "op", T0);
        }
        return r;
    }

    @Test
    void exactMatchProducesMatchedPendingAndDivergentSets() {
        var r = cededRegistry();
        var movements = LinkedAccountMovement.parseDeclaredFormat(String.join("\n",
                "R1;2026-11-09;750.00",      // casa exacto
                "R2;2026-11-09;499.99",      // valor divergente (1 centavo)
                "R9;2026-11-09;100.00"));    // sem baixa esperada
        var report = Reconciliation.reconcile(r, CessionFixtures.TENANT, movements, null);

        assertEquals(1, report.matched().size());
        assertEquals("R1", report.matched().get(0).receivableId());
        // Pendente: R4 sem movimento
        assertEquals(1, report.pending().size());
        assertEquals(Reconciliation.DivergenceKind.SEM_MOVIMENTO, report.pending().get(0).kind());
        assertEquals("R4", report.pending().get(0).receivableId());
        // Divergentes: R2 valor, R9 sem baixa esperada
        assertEquals(2, report.divergent().size());
        var kinds = report.divergent().stream().map(Reconciliation.Divergence::kind).toList();
        assertTrue(kinds.contains(Reconciliation.DivergenceKind.VALOR_DIVERGENTE));
        assertTrue(kinds.contains(Reconciliation.DivergenceKind.SEM_BAIXA_ESPERADA));
        var valorDiv = report.divergent().stream()
                .filter(d -> d.kind() == Reconciliation.DivergenceKind.VALOR_DIVERGENTE).findFirst().orElseThrow();
        assertEquals(new BigDecimal("500.00"), valorDiv.expectedAmount());
        assertEquals(new BigDecimal("499.99"), valorDiv.movementAmount());
    }

    @Test
    void declaredToleranceIsAParameterNotAPresumption() {
        var r = cededRegistry();
        var movements = List.of(new LinkedAccountMovement("R2", LocalDate.of(2026, 11, 9), new BigDecimal("499.99")));
        // Sem tolerancia declarada (null = exacto): R2 diverge
        var strict = Reconciliation.reconcile(r, CessionFixtures.TENANT, movements, null);
        assertTrue(strict.matched().isEmpty());
        // Com tolerancia declarada de 0.01: R2 casa
        var tolerant = Reconciliation.reconcile(r, CessionFixtures.TENANT, movements, new BigDecimal("0.01"));
        assertEquals(1, tolerant.matched().size());
        assertEquals("R2", tolerant.matched().get(0).receivableId());
    }

    @Test
    void reconciliationNeverMutatesTheRegistry() {
        var store = new InMemoryCessionStore();
        var r = new CessionRegistry(store);
        r.reserve(CessionFixtures.TENANT, "R1", "dev-hash-x", new BigDecimal("750.00"),
                "CONTA-VINCULADA-DECLARADA", "op", T0);
        r.cede(CessionFixtures.TENANT, "R1", "op", T0);
        var movements = List.of(new LinkedAccountMovement("R1", LocalDate.of(2026, 11, 9), new BigDecimal("750.00")));
        var report = Reconciliation.reconcile(r, CessionFixtures.TENANT, movements, null);
        assertEquals(1, report.matched().size());
        // Casar NAO liquida em silencio: o registo continua CEDIDO ate a
        // transicao explicita de liquidacao.
        assertEquals(CessionRegistry.Status.CEDIDO,
                store.activeRegistration(CessionFixtures.TENANT, "R1").orElseThrow().status());
    }

    @Test
    void malformedMovementFileIsRefusedWholesale() {
        assertEquals("MOVEMENT_LINE_MALFORMED", assertThrows(ReceivablesCessionException.class,
                () -> LinkedAccountMovement.parseDeclaredFormat("R1;2026-11-09")).code());
        assertEquals("MOVEMENT_LINE_MALFORMED", assertThrows(ReceivablesCessionException.class,
                () -> LinkedAccountMovement.parseDeclaredFormat("R1;09/11/2026;10.00")).code());
        var ok = LinkedAccountMovement.parseDeclaredFormat("\nR1;2026-11-09;10.00\n\n");
        assertEquals(1, ok.size());
    }
}
