package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** POC-44 (monitorar): prazo da P1.31, vencida, ordem do quadro. */
class Poc44MonitorTest {

    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 11, 9, 8, 0);

    private static OrderMonitoring.BoardItem item(String suffix, ServiceOrderStatus st, ServiceOrderEnums.Priority p, Integer deadlineHours) {
        return new OrderMonitoring.BoardItem(UUID.fromString("00000000-0000-0000-0000-0000000000" + suffix),
                st, p, OPEN_AT, deadlineHours, null, null);
    }

    @Test
    void dueFromTypeDeadlineAndOverdue() {
        OrderMonitoring.BoardItem i = item("c1", ServiceOrderStatus.OPEN, ServiceOrderEnums.Priority.NORMAL, 4);
        assertEquals(LocalDateTime.of(2026, 11, 9, 12, 0), OrderMonitoring.dueAt(i));
        assertEquals(OrderMonitoring.Timeliness.ON_TRACK, OrderMonitoring.timeliness(i, LocalDateTime.of(2026, 11, 9, 11, 0)));
        assertEquals(OrderMonitoring.Timeliness.OVERDUE, OrderMonitoring.timeliness(i, LocalDateTime.of(2026, 11, 9, 13, 0)));
        assertTrue(OrderMonitoring.remaining(i, LocalDateTime.of(2026, 11, 9, 13, 0)).isNegative());
    }

    @Test
    void noDeadlineAnywhereIsExplicitError() {
        OrderMonitoring.BoardItem i = item("c2", ServiceOrderStatus.OPEN, ServiceOrderEnums.Priority.NORMAL, null);
        assertThrows(IllegalStateException.class, () -> OrderMonitoring.dueAt(i));
    }

    @Test
    void boardUrgentFirstClosedLast() {
        OrderMonitoring.BoardItem normal = item("c3", ServiceOrderStatus.OPEN, ServiceOrderEnums.Priority.NORMAL, 2);
        OrderMonitoring.BoardItem urgent = item("c4", ServiceOrderStatus.OPEN, ServiceOrderEnums.Priority.URGENT, 48);
        OrderMonitoring.BoardItem closed = item("c5", ServiceOrderStatus.CLOSED_EXECUTED, ServiceOrderEnums.Priority.URGENT, 1);
        List<OrderMonitoring.BoardItem> board = OrderMonitoring.boardOrder(List.of(normal, closed, urgent),
                LocalDateTime.of(2026, 11, 9, 9, 0));
        assertEquals(urgent.orderId(), board.get(0).orderId());
        assertEquals(normal.orderId(), board.get(1).orderId());
        assertEquals(closed.orderId(), board.get(2).orderId());
        assertEquals(OrderMonitoring.Timeliness.CLOSED, OrderMonitoring.timeliness(closed, LocalDateTime.of(2027, 1, 1, 0, 0)));
    }
}
