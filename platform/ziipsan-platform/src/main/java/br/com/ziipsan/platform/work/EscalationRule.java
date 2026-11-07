package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Escalonamento por prazo conforme {@code wf.escalation_hours} (classe d): o item
 * VENCIDO escala quando {@code agora >= vencimento + escalation_hours}. Antes do
 * vencimento, nunca escala (prazo não é escalonamento antecipado); item encerrado
 * ou sem prazo nunca escala; item já escalonado não escala de novo nesta entrega
 * (um único escalonamento registrado por item — segundo nível é decisão de produto
 * que o aceite não pede).
 *
 * <p>Caso calculado à mão (teste): vencimento 2026-03-10T15:00:00Z, 24h → escala a
 * partir de 2026-03-11T15:00:00Z; às 14:59:59Z ainda não.
 */
public final class EscalationRule {

    private EscalationRule() {
    }

    /** Instante em que o item passa a dever escalonamento. {@code hours} negativo é erro do chamador. */
    public static Instant escalationDueAt(WorkItem item, long escalationHours) {
        if (item == null || item.dueAt() == null) {
            throw new IllegalArgumentException("escalonamento exige item com prazo");
        }
        if (escalationHours < 0) {
            throw new IllegalStateException("wf.escalation_hours não pode ser negativo; recebido: " + escalationHours);
        }
        return item.dueAt().plus(escalationHours, ChronoUnit.HOURS);
    }

    public static boolean isEscalationDue(WorkItem item, long escalationHours, Instant now) {
        if (item == null || now == null) {
            throw new IllegalArgumentException("item e instante são obrigatórios");
        }
        if (!item.status().isOpen() || item.dueAt() == null || item.escalatedAt() != null) {
            return false;
        }
        return !now.isBefore(escalationDueAt(item, escalationHours));
    }
}
