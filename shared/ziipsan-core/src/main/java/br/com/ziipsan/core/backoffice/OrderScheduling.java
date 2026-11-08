package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Programacao da OS no backoffice (ED-36; POC-45: programar e desprogramar):
 * agenda a OS numa janela de atendimento do tenant
 * ({@code sched.customer_windows}), com confirmacao e reagendamento pelo
 * usuario. As transicoes de estado sao as do motor da P1.31
 * ({@link ServiceOrderStatus} real): programar exige OPEN -> SCHEDULED;
 * DESPROGRAMAR nao tem aresta direta SCHEDULED -> OPEN no motor — o caminho
 * honesto e SCHEDULED -> WAITING_DEPENDENCY -> OPEN, e esta classe so
 * devolve esses passos validados, nunca um atalho inventado.
 *
 * <p>Capacidade: a janela tem capacidade por turno; a contagem de OS ja
 * agendadas na janela vem do chamador (persistencia) e o estouro e recusado.
 * O lembrete ao usuario e pelo canal da ED-41 QUANDO DISPONIVEL — aqui so
 * existe a porta {@link ReminderPort}; sem porta ligada, nenhum lembrete e
 * fingido.
 */
public final class OrderScheduling {

    /** Porta do lembrete (ED-41 quando disponivel; P3.5 ate la, pela fiacao). */
    public interface ReminderPort {
        void remind(UUID orderId, LocalDateTime start, String windowId);
    }

    public record Booking(UUID orderId, String windowId, LocalDateTime start, boolean customerConfirmed,
                          UUID scheduledBy, LocalDateTime scheduledAt) {
        public Booking {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            if (windowId == null || windowId.isBlank()) throw new IllegalArgumentException("janela do agendamento e obrigatoria");
            Objects.requireNonNull(start, "inicio agendado e obrigatorio");
            Objects.requireNonNull(scheduledBy, "quem agendou e obrigatorio");
            Objects.requireNonNull(scheduledAt, "quando agendou e obrigatorio");
        }
    }

    /** Registro de trilha de um reagendamento (motivo obrigatorio). */
    public record TrailEntry(UUID orderId, LocalDateTime previousStart, String previousWindowId,
                             LocalDateTime newStart, String newWindowId, String reason, UUID actorId, LocalDateTime at) {}

    public record RescheduleResult(Booking booking, TrailEntry trail) {}

    private OrderScheduling() {}

    public static Booking schedule(ServiceOrderStatus current, UUID orderId, SchedParameters.CustomerWindow window,
                                   LocalDateTime start, int alreadyBookedInWindow, UUID actorId, LocalDateTime now) {
        Objects.requireNonNull(current, "estado atual da OS e obrigatorio");
        Objects.requireNonNull(window, "janela de agendamento e obrigatoria");
        Objects.requireNonNull(start, "inicio e obrigatorio");
        Objects.requireNonNull(now, "instante atual e obrigatorio");
        if (!current.canMoveTo(ServiceOrderStatus.SCHEDULED))
            throw new IllegalStateException("programar exige transicao para SCHEDULED a partir de " + current + " (motor da P1.31)");
        checkWindow(window, start, alreadyBookedInWindow, now);
        return new Booking(orderId, window.id(), start, false, actorId, now);
    }

    public static Booking confirm(Booking booking) {
        Objects.requireNonNull(booking, "agendamento e obrigatorio");
        if (booking.customerConfirmed()) throw new IllegalStateException("agendamento ja confirmado pelo usuario");
        return new Booking(booking.orderId(), booking.windowId(), booking.start(), true,
                booking.scheduledBy(), booking.scheduledAt());
    }

    /** Reagendar mantem a OS em SCHEDULED (sem transicao de estado) e volta NAO confirmado: o usuario confirma a nova janela. */
    public static RescheduleResult reschedule(Booking booking, SchedParameters.CustomerWindow newWindow, LocalDateTime newStart,
                                              int alreadyBookedInWindow, String reason, UUID actorId, LocalDateTime now) {
        Objects.requireNonNull(booking, "agendamento e obrigatorio");
        Objects.requireNonNull(newWindow, "nova janela e obrigatoria");
        Objects.requireNonNull(newStart, "novo inicio e obrigatorio");
        Objects.requireNonNull(now, "instante atual e obrigatorio");
        Objects.requireNonNull(actorId, "autor do reagendamento e obrigatorio");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reagendamento exige motivo");
        checkWindow(newWindow, newStart, alreadyBookedInWindow, now);
        // O Booking devolvido e o agendamento NOVO: autor e instante sao os do
        // reagendamento (correcao D7 da revisao cruzada — antes herdava os
        // originais e quem lia so o Booking via o agendador errado).
        Booking next = new Booking(booking.orderId(), newWindow.id(), newStart, false, actorId, now);
        TrailEntry trail = new TrailEntry(booking.orderId(), booking.start(), booking.windowId(),
                newStart, newWindow.id(), reason.trim(), actorId, now);
        return new RescheduleResult(next, trail);
    }

    private static void checkWindow(SchedParameters.CustomerWindow window, LocalDateTime start,
                                    int alreadyBookedInWindow, LocalDateTime now) {
        if (!window.covers(start.getDayOfWeek(), start.toLocalTime()))
            throw new IllegalArgumentException("inicio " + start + " fora da janela " + window.id() + " (" + SchedParameters.CUSTOMER_WINDOWS + ")");
        if (start.isBefore(now)) throw new IllegalArgumentException("programacao no passado: " + start);
        if (alreadyBookedInWindow < 0) throw new IllegalArgumentException("contagem da janela nao pode ser negativa");
        if (alreadyBookedInWindow >= window.capacity())
            throw new IllegalStateException("janela " + window.id() + " lotada (" + window.capacity() + " OS) em " + start.toLocalDate());
    }

    /**
     * Desprogramar: devolve a sequencia de estados valida no motor da P1.31
     * (SCHEDULED -> WAITING_DEPENDENCY -> OPEN). Chamar com outro estado e
     * recusado; pular direto para OPEN seria transicao que o motor barra.
     */
    public static List<ServiceOrderStatus> unschedulePath(ServiceOrderStatus current) {
        if (current != ServiceOrderStatus.SCHEDULED)
            throw new IllegalStateException("desprogramar so se aplica a OS SCHEDULED (atual: " + current + ")");
        List<ServiceOrderStatus> path = List.of(ServiceOrderStatus.WAITING_DEPENDENCY, ServiceOrderStatus.OPEN);
        ServiceOrderStatus from = current;
        for (ServiceOrderStatus step : path) {
            if (!from.canMoveTo(step)) throw new IllegalStateException("motor da P1.31 recusa " + from + " -> " + step);
            from = step;
        }
        return path;
    }
}
