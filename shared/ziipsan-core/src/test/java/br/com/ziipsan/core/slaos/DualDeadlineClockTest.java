package br.com.ziipsan.core.slaos;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Estimativa II-4 §4.1 — duplo prazo: estados exactos nos dois sentidos. */
class DualDeadlineClockTest {

    private static final Instant OPEN = Instant.parse("2026-11-10T00:00:00Z");
    private final DualDeadlineClock clock =
            new DualDeadlineClock(Clock.fixed(OPEN, ZoneOffset.UTC));

    @Test
    void dentroDoInternoMasForaDoRegulador() {
        // interno 48h, regulador 24h; em +30h: regulador BREACHED, interno vivo.
        var cfg = new TypeSlaConfig("TST-TIPO", 48, 24, 90, List.of());
        var a = clock.evaluate(OPEN, cfg, OPEN.plusSeconds(30 * 3600L));
        assertEquals(DualDeadlineClock.DeadlineState.BREACHED, a.regulator().state());
        assertEquals(DualDeadlineClock.DeadlineState.WITHIN, a.internal().state());
    }

    @Test
    void foraDoInternoMasDentroDoRegulador() {
        // interno 24h, regulador 72h; em +30h: interno BREACHED, regulador vivo.
        var cfg = new TypeSlaConfig("TST-TIPO", 24, 72, 90, List.of());
        var a = clock.evaluate(OPEN, cfg, OPEN.plusSeconds(30 * 3600L));
        assertEquals(DualDeadlineClock.DeadlineState.BREACHED, a.internal().state());
        assertEquals(DualDeadlineClock.DeadlineState.WITHIN, a.regulator().state());
    }

    @Test
    void quebraExactamenteNoVencimento() {
        var cfg = new TypeSlaConfig("TST-TIPO", 24, 24, 50, List.of());
        var a = clock.evaluate(OPEN, cfg, OPEN.plusSeconds(24 * 3600L));
        assertEquals(DualDeadlineClock.DeadlineState.BREACHED, a.internal().state());
        assertEquals(DualDeadlineClock.DeadlineState.BREACHED, a.regulator().state());
    }

    @Test
    void prazoNaoConfiguradoNaoInventaEstado() {
        // prazos [A DEFINIR]: NOT_CONFIGURED e sem vencimento afirmado.
        var cfg = new TypeSlaConfig("TST-TIPO", null, null, 80, List.of());
        var a = clock.evaluate(OPEN, cfg, OPEN.plusSeconds(999 * 3600L));
        assertEquals(DualDeadlineClock.DeadlineState.NOT_CONFIGURED, a.internal().state());
        assertEquals(DualDeadlineClock.DeadlineState.NOT_CONFIGURED, a.regulator().state());
        assertNull(a.internal().dueAtOrNull());
        assertNull(a.regulator().dueAtOrNull());
    }
}
