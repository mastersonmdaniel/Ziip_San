package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Monitoramento da OS no backoffice (ED-36; POC-44: monitorar). O prazo
 * exibido e o da P1.31: due_at gravado na OS quando existe; na falta, a
 * abertura + deadline_hours do tipo (ja com o override so-reduz de
 * so.deadline_hours_by_type aplicado pelo chamador). Sem nenhum dos dois,
 * nao ha prazo a monitorar — erro explicito, nunca prazo inventado.
 *
 * <p>Ordem do quadro: encerradas por ultimo; entre as abertas, prioridade
 * (urgencia sempre no topo — sched.priority_weights, classe d, do produto)
 * e prazo mais cedo primeiro; vencida sobe dentro da sua prioridade.
 */
public final class OrderMonitoring {

    public record BoardItem(UUID orderId, ServiceOrderStatus status, ServiceOrderEnums.Priority priority,
                            LocalDateTime createdAt, Integer deadlineHoursOrNull, LocalDateTime dueAtOrNull,
                            LocalDateTime scheduledForOrNull) {
        public BoardItem {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            Objects.requireNonNull(status, "estado e obrigatorio");
            Objects.requireNonNull(priority, "prioridade e obrigatoria");
            Objects.requireNonNull(createdAt, "abertura e obrigatoria");
            if (deadlineHoursOrNull != null && deadlineHoursOrNull <= 0) throw new IllegalArgumentException("deadline_hours deve ser positivo");
        }
    }

    public enum Timeliness { ON_TRACK, OVERDUE, CLOSED }

    private OrderMonitoring() {}

    public static LocalDateTime dueAt(BoardItem item) {
        if (item.dueAtOrNull() != null) return item.dueAtOrNull();
        if (item.deadlineHoursOrNull() != null) return item.createdAt().plusHours(item.deadlineHoursOrNull());
        throw new IllegalStateException("OS " + item.orderId() + " sem due_at e sem deadline_hours do tipo: prazo da P1.31 nao definido");
    }

    public static Timeliness timeliness(BoardItem item, LocalDateTime now) {
        Objects.requireNonNull(now, "instante de referencia e obrigatorio");
        if (item.status().isTerminal()) return Timeliness.CLOSED;
        return now.isAfter(dueAt(item)) ? Timeliness.OVERDUE : Timeliness.ON_TRACK;
    }

    /** Negativo quando ja venceu. */
    public static Duration remaining(BoardItem item, LocalDateTime now) {
        return Duration.between(now, dueAt(item));
    }

    public static List<BoardItem> boardOrder(List<BoardItem> items, LocalDateTime now) {
        Objects.requireNonNull(items, "itens do quadro sao obrigatorios");
        return items.stream().sorted(Comparator
                .comparingInt((BoardItem i) -> i.status().isTerminal() ? 1 : 0)
                .thenComparingInt(i -> SchedulePlanner.priorityRank(i.priority()))
                .thenComparing(i -> timeliness(i, now) == Timeliness.OVERDUE ? 0 : 1)
                .thenComparing(OrderMonitoring::dueAt)
                .thenComparing(i -> i.orderId().toString())).toList();
    }
}
