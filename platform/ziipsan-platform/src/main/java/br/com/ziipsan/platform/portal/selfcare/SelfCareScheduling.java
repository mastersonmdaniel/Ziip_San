package br.com.ziipsan.platform.portal.selfcare;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agendamento pelo usuario (ED-38) nas JANELAS DA ED-36: esta classe NAO
 * define janela nem regra de agenda — lista as janelas oferecidas pela
 * porta (modelo {@code sched.customer_windows} da ED-36) e reserva pela
 * porta que delega a {@code OrderScheduling} da ED-36 (marcacao nasce
 * NAO confirmada; confirmacao e reagendamento com motivo seguem a
 * ED-36). Janela lotada ou inexistente recusa; o lembrete e da ED-41
 * (fora desta entrega, como na ED-36).
 */
public final class SelfCareScheduling {

    public record WindowOffer(String windowId, LocalDateTime start, int freeSlots) {
        public WindowOffer {
            if (windowId == null || windowId.isBlank() || start == null || freeSlots < 0) {
                throw new IllegalArgumentException("janela oferecida invalida");
            }
        }
    }

    public interface SchedulingPort {
        List<WindowOffer> offeredWindows(UUID tenantId, UUID serviceOrderId);
        /** Delega a OrderScheduling da ED-36; devolve o agendamento (nao confirmado). */
        String book(UUID tenantId, UUID serviceOrderId, String windowId, LocalDateTime start);
        String confirm(UUID tenantId, String bookingRef);
    }

    private final SchedulingPort port;

    public SelfCareScheduling(SchedulingPort port) {
        if (port == null) {
            throw new IllegalArgumentException("porta de agendamento (ED-36) e obrigatoria");
        }
        this.port = port;
    }

    public List<WindowOffer> windows(UUID tenantId, UUID serviceOrderId) {
        if (tenantId == null || serviceOrderId == null) {
            throw new IllegalArgumentException("tenant e OS sao obrigatorios");
        }
        return port.offeredWindows(tenantId, serviceOrderId).stream().filter(w -> w.freeSlots() > 0).toList();
    }

    public String book(UUID tenantId, UUID serviceOrderId, String windowId, LocalDateTime start) {
        boolean offered = windows(tenantId, serviceOrderId).stream()
                .anyMatch(w -> w.windowId().equals(windowId) && w.start().equals(start));
        if (!offered) {
            throw new IllegalStateException("janela nao oferecida ou lotada para a OS (ED-36)");
        }
        return port.book(tenantId, serviceOrderId, windowId, start);
    }

    public String confirm(UUID tenantId, String bookingRef) {
        if (bookingRef == null || bookingRef.isBlank()) {
            throw new IllegalArgumentException("agendamento e obrigatorio");
        }
        return port.confirm(tenantId, bookingRef);
    }
}
