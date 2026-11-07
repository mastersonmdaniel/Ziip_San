package br.com.ziipsan.platform.work;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Escalonamento por prazo (wf.escalation_hours), calculado à mão:
 * vencimento 2026-03-10T15:00:00Z + 24h = escala a partir de 2026-03-11T15:00:00Z.
 */
class EscalationRuleTest {

    private static final Instant DUE = Instant.parse("2026-03-10T15:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-03-08T15:00:00Z");

    private static WorkItem item(WorkItemStatus status, Instant dueAt, Instant escalatedAt) {
        boolean terminal = status.isTerminal();
        return new WorkItem(UUID.randomUUID(), UUID.randomUUID(), WorkItemType.PROTOCOL_DUE,
                "PROTOCOL", UUID.randomUUID(), "sac", UUID.randomUUID(), UUID.randomUUID(), Set.of(), null,
                dueAt, status, "/protocols/1", "protocol.due-soon", 1, null, null, CREATED,
                terminal ? CREATED : null, terminal ? "DONE" : null, escalatedAt, escalatedAt == null ? 0 : 1, null);
    }

    @Test
    @DisplayName("REGRA DO ACEITE: item VENCIDO escala — na hora exata (vencimento + wf.escalation_hours) e depois dela")
    void overdueItemEscalates() {
        WorkItem overdue = item(WorkItemStatus.OPEN, DUE, null);
        assertThat(EscalationRule.escalationDueAt(overdue, 24)).isEqualTo(Instant.parse("2026-03-11T15:00:00Z"));
        assertThat(EscalationRule.isEscalationDue(overdue, 24, Instant.parse("2026-03-11T15:00:00Z"))).isTrue();
        assertThat(EscalationRule.isEscalationDue(overdue, 24, Instant.parse("2026-03-15T15:00:00Z"))).isTrue();
        // Com 0 horas, escala no próprio vencimento.
        assertThat(EscalationRule.isEscalationDue(overdue, 0, DUE)).isTrue();
    }

    @Test
    @DisplayName("antes do vencimento + janela, NÃO escala: nem no vencimento puro (com 24h), nem 1 segundo antes da hora de escalar")
    void notYetDueDoesNotEscalate() {
        WorkItem item = item(WorkItemStatus.OPEN, DUE, null);
        assertThat(EscalationRule.isEscalationDue(item, 24, DUE)).isFalse();
        assertThat(EscalationRule.isEscalationDue(item, 24, Instant.parse("2026-03-11T14:59:59Z"))).isFalse();
        assertThat(EscalationRule.isEscalationDue(item, 24, Instant.parse("2026-03-09T15:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("encerrado, sem prazo ou já escalonado nunca escala (de novo)")
    void closedNoDueOrAlreadyEscalatedNeverEscalate() {
        Instant after = Instant.parse("2026-04-01T00:00:00Z");
        assertThat(EscalationRule.isEscalationDue(item(WorkItemStatus.DONE, DUE, null), 24, after)).isFalse();
        assertThat(EscalationRule.isEscalationDue(item(WorkItemStatus.OPEN, null, null), 24, after)).isFalse();
        assertThat(EscalationRule.isEscalationDue(
                item(WorkItemStatus.ESCALATED, DUE, Instant.parse("2026-03-11T15:00:00Z")), 24, after)).isFalse();
        assertThatThrownBy(() -> EscalationRule.escalationDueAt(item(WorkItemStatus.OPEN, null, null), 24))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EscalationRule.isEscalationDue(item(WorkItemStatus.OPEN, DUE, null), -1, after))
                .isInstanceOf(IllegalStateException.class);
    }
}
