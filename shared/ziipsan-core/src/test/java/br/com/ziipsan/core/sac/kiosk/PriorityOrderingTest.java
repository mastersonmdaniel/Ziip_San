package br.com.ziipsan.core.sac.kiosk;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PriorityOrderingTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static QueueTicket ticket(int seq, boolean priority, LocalDateTime issued) {
        var profile = priority
                ? PriorityAttendanceProfile.priority(PriorityAttendanceProfile.PriorityCategory.ELDERLY_60_OR_OVER)
                : PriorityAttendanceProfile.common();
        return new QueueTicket(UUID.randomUUID(), UNIT, seq, "ATENDIMENTO", profile, issued);
    }

    @Test void legalPriorityGoesFirstEvenWhenIssuedLater() {
        var base = LocalDateTime.of(2026, 3, 4, 8, 0);
        var normal1 = ticket(1, false, base);
        var normal2 = ticket(2, false, base.plusMinutes(1));
        var prio = ticket(3, true, base.plusMinutes(5));
        var ordered = TicketQueue.orderWaiting(List.of(normal1, normal2, prio));
        assertEquals(List.of(prio, normal1, normal2), ordered);
        assertEquals(prio, TicketQueue.nextToCall(List.of(normal1, normal2, prio)).orElseThrow());
    }

    @Test void fifoWithinSameClass() {
        var base = LocalDateTime.of(2026, 3, 4, 8, 0);
        var p1 = ticket(1, true, base.plusMinutes(10));
        var p2 = ticket(2, true, base.plusMinutes(2));
        var ordered = TicketQueue.orderWaiting(List.of(p1, p2));
        assertEquals(List.of(p2, p1), ordered);
    }

    @Test void calledTicketsLeaveTheWaitingOrder() {
        var base = LocalDateTime.of(2026, 3, 4, 8, 0);
        var t = ticket(1, false, base);
        t.call(base.plusMinutes(3), "G1");
        assertTrue(TicketQueue.orderWaiting(List.of(t)).isEmpty());
        assertTrue(TicketQueue.nextToCall(List.of(t)).isEmpty());
    }

    @Test void allLegalCategoriesFromP13ProfileArePriority() {
        for (var c : PriorityAttendanceProfile.PriorityCategory.values()) {
            var p = new PriorityAttendanceProfile(c, false,
                    PriorityAttendanceProfile.AccessibleFormat.NONE,
                    PriorityAttendanceProfile.AssistanceChannel.NONE);
            assertEquals(c != PriorityAttendanceProfile.PriorityCategory.NONE, p.legalPriority(), c.name());
        }
    }

    @Test void displayCodeMarksPriority() {
        var base = LocalDateTime.of(2026, 3, 4, 8, 0);
        assertEquals("P007", ticket(7, true, base).displayCode());
        assertEquals("N007", ticket(7, false, base).displayCode());
    }
}
