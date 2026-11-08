package br.com.ziipsan.core.sac.kiosk;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ordenacao da fila presencial: prioridade LEGAL primeiro (perfil da
 * P1.3/CAD-81, Lei 10.048/2000), depois ordem de emissao — mesma regra da
 * fila por prazo da ED-35 ({@code ServiceQueue}: quem tem prioridade nao
 * espera atras de quem nao tem). Desempate final pelo sequencial da senha.
 */
public final class TicketQueue {

    private TicketQueue() {}

    public static List<QueueTicket> orderWaiting(List<QueueTicket> tickets) {
        return tickets.stream()
                .filter(t -> t.status() == QueueTicket.Status.WAITING)
                .sorted(Comparator.comparing((QueueTicket t) -> t.profile().legalPriority()).reversed()
                        .thenComparing(QueueTicket::issuedAt)
                        .thenComparingInt(QueueTicket::sequenceNumber)
                        .thenComparing(t -> t.id().toString()))
                .toList();
    }

    public static Optional<QueueTicket> nextToCall(List<QueueTicket> tickets) {
        List<QueueTicket> ordered = orderWaiting(tickets);
        return ordered.isEmpty() ? Optional.empty() : Optional.of(ordered.get(0));
    }
}
