package br.com.ziipsan.core.slaos;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimativa II-4 §4.3 — escalonamento: sequencia de niveis por tempo decorrido, deterministica. */
class EscalationSequenceTest {

    private static final Instant OPEN = Instant.parse("2026-11-10T00:00:00Z");
    private final DualDeadlineClock clock =
            new DualDeadlineClock(Clock.fixed(OPEN, ZoneOffset.UTC));
    // prazo unico de referencia 100h (os dois iguais); degraus 80/100/150 (%).
    private final TypeSlaConfig cfg = new TypeSlaConfig("TST-TIPO", 100, 100, 80, List.of(80, 100, 150));

    @Test
    void sequenciaDeNiveisPorTempoDecorrido() {
        assertEquals(0, SlaEscalation.escalationLevel(OPEN, cfg, OPEN.plusSeconds(79 * 3600L)));
        assertEquals(1, SlaEscalation.escalationLevel(OPEN, cfg, OPEN.plusSeconds(80 * 3600L)));
        assertEquals(2, SlaEscalation.escalationLevel(OPEN, cfg, OPEN.plusSeconds(100 * 3600L)));
        assertEquals(2, SlaEscalation.escalationLevel(OPEN, cfg, OPEN.plusSeconds(149 * 3600L)));
        assertEquals(3, SlaEscalation.escalationLevel(OPEN, cfg, OPEN.plusSeconds(150 * 3600L)));
    }

    @Test
    void determinismoMesmasEntradasMesmosEventos() {
        Instant t = OPEN.plusSeconds(120 * 3600L);
        var first = SlaEscalation.eventsAt(OPEN, cfg, t, clock);
        var second = SlaEscalation.eventsAt(OPEN, cfg, t, clock);
        assertEquals(first, second);
        // em +120h: BREACH nos dois prazos + ESCALATED nivel 2, nesta ordem.
        assertEquals(3, first.size());
        assertEquals(SlaEscalation.EventKind.BREACH, first.get(0).kind());
        assertEquals(SlaEscalation.EventKind.BREACH, first.get(1).kind());
        assertEquals(SlaEscalation.EventKind.ESCALATED, first.get(2).kind());
        assertEquals(2, first.get(2).escalationLevel());
    }

    @Test
    void semPrazoConfiguradoNaoEscalona() {
        var semPrazo = new TypeSlaConfig("TST-TIPO", null, null, 80, List.of(80, 100));
        assertEquals(0, SlaEscalation.escalationLevel(OPEN, semPrazo, OPEN.plusSeconds(500 * 3600L)));
        assertTrue(SlaEscalation.eventsAt(OPEN, semPrazo, OPEN.plusSeconds(500 * 3600L), clock).isEmpty());
    }
}
