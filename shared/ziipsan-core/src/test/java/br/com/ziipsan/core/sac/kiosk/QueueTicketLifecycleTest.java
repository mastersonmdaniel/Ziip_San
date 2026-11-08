package br.com.ziipsan.core.sac.kiosk;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QueueTicketLifecycleTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 3, 4, 8, 0);

    private static QueueTicket ticket() {
        return new QueueTicket(UUID.randomUUID(), UNIT, 1, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), ISSUED);
    }

    @Test void threeTimestampsAreRecordedPerTicket() {
        var t = ticket();
        t.call(ISSUED.plusMinutes(6), "G2");
        t.startService(ISSUED.plusMinutes(9));
        t.complete(ISSUED.plusMinutes(20));
        assertEquals(ISSUED, t.issuedAt());
        assertEquals(ISSUED.plusMinutes(6), t.calledAt());
        assertEquals(ISSUED.plusMinutes(9), t.serviceStartedAt());
        assertEquals("G2", t.deskId());
        assertEquals(QueueTicket.Status.COMPLETED, t.status());
        assertEquals(Duration.ofMinutes(6), t.waitUntilCall());
        assertEquals(Duration.ofMinutes(9), t.waitingTime());
    }

    @Test void callBeforeIssuanceRefused() {
        var t = ticket();
        assertThrows(IllegalArgumentException.class, () -> t.call(ISSUED.minusMinutes(1), "G1"));
    }

    @Test void doubleCallAndStartBeforeCallRefused() {
        var t = ticket();
        assertThrows(IllegalStateException.class, () -> t.startService(ISSUED.plusMinutes(1)));
        t.call(ISSUED.plusMinutes(2), "G1");
        assertThrows(IllegalStateException.class, () -> t.call(ISSUED.plusMinutes(3), "G1"));
    }

    @Test void startBeforeCallTimeRefused() {
        var t = ticket();
        t.call(ISSUED.plusMinutes(10), "G1");
        assertThrows(IllegalArgumentException.class, () -> t.startService(ISSUED.plusMinutes(5)));
    }

    @Test void noShowOnlyAfterCall() {
        var t = ticket();
        assertThrows(IllegalStateException.class, () -> t.markNoShow(ISSUED.plusMinutes(1)));
        t.call(ISSUED.plusMinutes(2), "G1");
        t.markNoShow(ISSUED.plusMinutes(7));
        assertEquals(QueueTicket.Status.NO_SHOW, t.status());
        assertNull(t.waitingTime());
    }

    @Test void blankServiceTypeAndNonPositiveSequenceRefused() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTicket(UUID.randomUUID(), UNIT, 1, " ",
                PriorityAttendanceProfile.common(), ISSUED));
        assertThrows(IllegalArgumentException.class, () -> new QueueTicket(UUID.randomUUID(), UNIT, 0, "X",
                PriorityAttendanceProfile.common(), ISSUED));
    }
}
