package br.com.ziipsan.core.sac.kiosk;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KioskAndPanelTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 4, 8, 0);

    @Test void kioskDisabledRefusesIssuance() {
        var off = KioskTerminal.fromParameters(DEVICE, UNIT, false);
        assertThrows(IllegalStateException.class, () -> off.issueTicket(
                UUID.randomUUID(), 1, "ATENDIMENTO", PriorityAttendanceProfile.common(), T0));
        assertFalse(off.actionAllowed(KioskTerminal.KioskAction.ISSUE_TICKET));
    }

    @Test void kioskEnabledIssuesTicketForItsUnit() {
        var on = KioskTerminal.fromParameters(DEVICE, UNIT, true);
        var t = on.issueTicket(UUID.randomUUID(), 3, "ATENDIMENTO",
                PriorityAttendanceProfile.priority(
                        PriorityAttendanceProfile.PriorityCategory.PERSON_WITH_DISABILITY), T0);
        assertEquals(UNIT, t.serviceUnitId());
        assertEquals("P003", t.displayCode());
        assertEquals(T0, t.issuedAt());
        assertTrue(on.actionAllowed(KioskTerminal.KioskAction.VIEW_CALL_PANEL));
    }

    @Test void panelShowsCurrentAndHistoryByCallOrderWithoutNames() {
        var a = new QueueTicket(UUID.randomUUID(), UNIT, 1, "ATENDIMENTO", PriorityAttendanceProfile.common(), T0);
        var b = new QueueTicket(UUID.randomUUID(), UNIT, 2, "ATENDIMENTO", PriorityAttendanceProfile.common(), T0.plusMinutes(1));
        var other = new QueueTicket(UUID.randomUUID(), UUID.randomUUID(), 9, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), T0);
        a.call(T0.plusMinutes(5), "G1");
        b.call(T0.plusMinutes(9), "G2");
        other.call(T0.plusMinutes(12), "G9");
        var view = CallPanel.snapshot(List.of(a, b, other), UNIT, 5);
        assertNotNull(view.current());
        assertEquals("N002", view.current().displayCode());
        assertEquals("G2", view.current().deskId());
        assertEquals(T0.plusMinutes(9), view.current().calledAt());
        assertEquals(1, view.history().size());
        assertEquals("N001", view.history().get(0).displayCode());
    }

    @Test void emptyPanelHasNoCurrent() {
        var view = CallPanel.snapshot(List.of(), UNIT, 5);
        assertNull(view.current());
        assertTrue(view.history().isEmpty());
    }
}
