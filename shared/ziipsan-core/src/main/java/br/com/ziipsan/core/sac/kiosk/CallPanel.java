package br.com.ziipsan.core.sac.kiosk;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Painel de chamada (SG-626, em navegador) e chamada na tela de
 * atendimento (SG-625). O painel exibe SO codigo da senha, guiche e
 * instante da chamada — nenhum nome ou dado pessoal (LGPD): quem esta na
 * sala reconhece a propria senha pelo codigo impresso no papel.
 */
public final class CallPanel {

    public record CallAnnouncement(String displayCode, String deskId, UUID serviceUnitId,
                                   LocalDateTime calledAt, boolean legalPriority) {
        public CallAnnouncement {
            Objects.requireNonNull(displayCode, "codigo da senha obrigatorio");
            Objects.requireNonNull(deskId, "guiche obrigatorio");
            Objects.requireNonNull(serviceUnitId, "unidade obrigatoria");
            Objects.requireNonNull(calledAt, "hora da chamada obrigatoria");
        }
    }

    /** current = chamada mais recente; history = anteriores, da mais recente para a mais antiga. */
    public record PanelView(CallAnnouncement current, List<CallAnnouncement> history) {}

    private CallPanel() {}

    public static PanelView snapshot(List<QueueTicket> tickets, UUID serviceUnitId, int historySize) {
        Objects.requireNonNull(tickets, "senhas obrigatorias");
        Objects.requireNonNull(serviceUnitId, "unidade obrigatoria");
        if (historySize < 0) throw new IllegalArgumentException("historico nao pode ser negativo");
        List<CallAnnouncement> calls = tickets.stream()
                .filter(t -> t.serviceUnitId().equals(serviceUnitId))
                .filter(t -> t.calledAt() != null)
                .sorted(Comparator.comparing(QueueTicket::calledAt).reversed()
                        .thenComparing(QueueTicket::displayCode))
                .map(t -> new CallAnnouncement(t.displayCode(), t.deskId(), t.serviceUnitId(),
                        t.calledAt(), t.profile().legalPriority()))
                .toList();
        if (calls.isEmpty()) return new PanelView(null, List.of());
        List<CallAnnouncement> history = calls.stream().skip(1).limit(historySize).toList();
        return new PanelView(calls.get(0), history);
    }
}
