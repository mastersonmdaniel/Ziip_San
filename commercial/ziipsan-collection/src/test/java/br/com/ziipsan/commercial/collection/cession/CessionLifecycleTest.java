package br.com.ziipsan.commercial.collection.cession;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** ED-103/E4 — ciclo de vida, trava de dupla cessao e concorrencia. */
class CessionLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    private CessionRegistry registry() {
        return new CessionRegistry(new InMemoryCessionStore());
    }

    private CessionRegistry.Registration reserve(CessionRegistry r, String receivable) {
        return r.reserve(CessionFixtures.TENANT, receivable, "dev-hash-001",
                new BigDecimal("750.00"), "FUNDO-DECLARADO", "operador-1", T0);
    }

    @Test
    void legalPathReserveCedeLiquidate() {
        var r = registry();
        var reg = reserve(r, "R1");
        assertEquals(CessionRegistry.Status.RESERVADO, reg.status());
        reg = r.cede(CessionFixtures.TENANT, "R1", "operador-1", T0);
        assertEquals(CessionRegistry.Status.CEDIDO, reg.status());
        reg = r.liquidate(CessionFixtures.TENANT, "R1", "operador-1", T0);
        assertEquals(CessionRegistry.Status.LIQUIDADO, reg.status());
        assertEquals(3, reg.events().size());
    }

    @Test
    void releaseOnlyFromReservedAndFreesTheReceivable() {
        var r = registry();
        reserve(r, "R1");
        var released = r.release(CessionFixtures.TENANT, "R1", "operador-1", T0);
        assertEquals(CessionRegistry.Status.LIBERTADO, released.status());
        // Libertado: novo ciclo pode comecar (novo registo)
        var again = reserve(r, "R1");
        assertEquals(CessionRegistry.Status.RESERVADO, again.status());
        assertNotEquals(released.id(), again.id());
    }

    @Test
    void illegalTransitionsAreRefusedWithTypedError() {
        var r = registry();
        // Sem registo activo
        assertEquals("CESSION_NO_ACTIVE_REGISTRATION", assertThrows(ReceivablesCessionException.class,
                () -> r.cede(CessionFixtures.TENANT, "R9", "op", T0)).code());
        reserve(r, "R1");
        // RESERVADO -> LIQUIDADO e ilegal (so de CEDIDO)
        assertEquals("ILLEGAL_TRANSITION", assertThrows(ReceivablesCessionException.class,
                () -> r.liquidate(CessionFixtures.TENANT, "R1", "op", T0)).code());
        r.cede(CessionFixtures.TENANT, "R1", "op", T0);
        // CEDIDO -> LIBERTADO e ilegal (de CEDIDO so LIQUIDADO ou reversao)
        assertEquals("ILLEGAL_TRANSITION", assertThrows(ReceivablesCessionException.class,
                () -> r.release(CessionFixtures.TENANT, "R1", "op", T0)).code());
        // CEDIDO -> CEDIDO e ilegal
        assertEquals("ILLEGAL_TRANSITION", assertThrows(ReceivablesCessionException.class,
                () -> r.cede(CessionFixtures.TENANT, "R1", "op", T0)).code());
        r.liquidate(CessionFixtures.TENANT, "R1", "op", T0);
        // Terminal nao transita
        assertEquals("CESSION_NO_ACTIVE_REGISTRATION", assertThrows(ReceivablesCessionException.class,
                () -> r.release(CessionFixtures.TENANT, "R1", "op", T0)).code());
    }

    @Test
    void doubleCessionIsBlockedWhileActive() {
        var r = registry();
        reserve(r, "R1");
        assertEquals("DOUBLE_CESSION_BLOCKED", assertThrows(ReceivablesCessionException.class,
                () -> reserve(r, "R1")).code());
        r.cede(CessionFixtures.TENANT, "R1", "op", T0);
        assertEquals("DOUBLE_CESSION_BLOCKED", assertThrows(ReceivablesCessionException.class,
                () -> reserve(r, "R1")).code());
        // Outro recebivel nao e afectado pela trava
        assertEquals(CessionRegistry.Status.RESERVADO, reserve(r, "R2").status());
    }

    @Test
    void reversalFromCededRequiresExplicitReasonAndIsRecorded() {
        var r = registry();
        reserve(r, "R1");
        r.cede(CessionFixtures.TENANT, "R1", "op", T0);
        assertEquals("CESSION_REVERSAL_REASON_REQUIRED", assertThrows(ReceivablesCessionException.class,
                () -> r.revert(CessionFixtures.TENANT, "R1", " ", "op", T0)).code());
        var reverted = r.revert(CessionFixtures.TENANT, "R1", "desistencia declarada do cessionario", "op", T0);
        assertEquals(CessionRegistry.Status.REVERTIDO, reverted.status());
        var last = reverted.events().get(reverted.events().size() - 1);
        assertEquals(CessionRegistry.Status.CEDIDO, last.from());
        assertEquals(CessionRegistry.Status.REVERTIDO, last.to());
        assertEquals("desistencia declarada do cessionario", last.reason());
    }

    @Test
    void concurrentReservationsOfSameReceivableYieldExactlyOneSuccess() throws Exception {
        var r = registry();
        int threads = 16;
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var blocked = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        reserve(r, "R-CONC");
                        successes.incrementAndGet();
                    } catch (ReceivablesCessionException e) {
                        if ("DOUBLE_CESSION_BLOCKED".equals(e.code())) blocked.incrementAndGet();
                        else synchronized (unexpected) { unexpected.add(e); }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            start.countDown();
            for (var f : futures) f.get(30, TimeUnit.SECONDS);
        }
        assertTrue(unexpected.isEmpty(), "erros inesperados: " + unexpected);
        assertEquals(1, successes.get(), "exactamente uma reserva pode vencer a trava");
        assertEquals(threads - 1, blocked.get());
    }
}
