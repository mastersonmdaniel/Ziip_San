package br.com.ziipsan.core.slaos;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimativa II-4 §4.2 — alerta dispara no limiar configurado (fronteira incluida) e nao antes. */
class RiskAlertBoundaryTest {

    private static final Instant OPEN = Instant.parse("2026-11-10T00:00:00Z");
    private final DualDeadlineClock clock =
            new DualDeadlineClock(Clock.fixed(OPEN, ZoneOffset.UTC));

    @Test
    void naoDisparaAntesDoLimiar() {
        // prazo 100h, limiar 80%: em +79h59m59s ainda WITHIN.
        var cfg = new TypeSlaConfig("TST-TIPO", 100, 100, 80, List.of());
        Instant t = OPEN.plusSeconds(79 * 3600L + 3599);
        var a = clock.evaluate(OPEN, cfg, t);
        assertEquals(DualDeadlineClock.DeadlineState.WITHIN, a.internal().state());
        assertTrue(SlaEscalation.eventsAt(OPEN, cfg, t, clock).isEmpty());
    }

    @Test
    void disparaExactamenteNoLimiar() {
        // em +80h00m00s: AT_RISK (fronteira incluida) + evento RISK_ALERT.
        var cfg = new TypeSlaConfig("TST-TIPO", 100, 100, 80, List.of());
        Instant t = OPEN.plusSeconds(80 * 3600L);
        var a = clock.evaluate(OPEN, cfg, t);
        assertEquals(DualDeadlineClock.DeadlineState.AT_RISK, a.internal().state());
        var events = SlaEscalation.eventsAt(OPEN, cfg, t, clock);
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> e.kind() == SlaEscalation.EventKind.RISK_ALERT));
    }

    @Test
    void limiarEConfiguravel() {
        // mesmo instante (+50h de 100h): limiar 80% -> WITHIN; limiar 50% -> AT_RISK.
        Instant t = OPEN.plusSeconds(50 * 3600L);
        var cfg80 = new TypeSlaConfig("TST-TIPO", 100, 100, 80, List.of());
        var cfg50 = new TypeSlaConfig("TST-TIPO", 100, 100, 50, List.of());
        assertEquals(DualDeadlineClock.DeadlineState.WITHIN, clock.evaluate(OPEN, cfg80, t).internal().state());
        assertEquals(DualDeadlineClock.DeadlineState.AT_RISK, clock.evaluate(OPEN, cfg50, t).internal().state());
    }
}
