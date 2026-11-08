package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-93: OS em execucao e vencidas (regra de prazo REAL da ED-36). */
class WorkOrderProjectionTest {

    private static SituationFeeds.WorkOrderSnapshot order(ServiceOrderStatus status, Integer deadlineHours,
                                                          java.time.LocalDateTime dueAt) {
        return new SituationFeeds.WorkOrderSnapshot(UUID.randomUUID(), status,
                ServiceOrderEnums.Priority.NORMAL, SituationFixtures.NOW_LOCAL.minusHours(5),
                deadlineHours, dueAt, "EQ-1", null, null);
    }

    private SituationSnapshot snapshotWith(List<SituationFeeds.WorkOrderSnapshot> orders) {
        SituationProjector projector = SituationFixtures.projector(
                t -> List.of(), t -> orders, t -> List.of(), t -> List.of(), t -> List.of(), t -> List.of());
        InMemorySituationStateStore store = new InMemorySituationStateStore();
        projector.projectInto(SituationFixtures.TENANT, store);
        return new SituationConsoleService(store).snapshot(SituationFixtures.TENANT,
                SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE,
                SituationFixtures.POLICY_3MIN, SituationFixtures.NOW);
    }

    @Test
    void executingOrdersAreSeparated() {
        SituationSnapshot snapshot = snapshotWith(List.of(
                order(ServiceOrderStatus.IN_EXECUTION, null, SituationFixtures.NOW_LOCAL.plusHours(2))));
        assertEquals(1, snapshot.workOrdersExecuting().size());
        assertTrue(snapshot.workOrdersOverdue().isEmpty());
    }

    @Test
    void executingAndOverdueOrderAppearsInOverdueAsWellAsExecuting() {
        // D1 (revisao ED-93-REV-1): a combinacao IN_EXECUTION + OVERDUE
        // (abertura ha 5h, deadline de 2h do tipo, regra real da ED-36)
        // tem de aparecer nas VENCIDAS — vencida e predicado independente
        // do estado de execucao. Aparece tambem em execucao (os dois
        // conjuntos do aceite nao sao disjuntos), fora do residual, e
        // UMA vez no retrato consolidado (allRows sem duplicar).
        SituationSnapshot snapshot = snapshotWith(List.of(order(ServiceOrderStatus.IN_EXECUTION, 2, null)));
        assertEquals(1, snapshot.workOrdersOverdue().size());
        assertEquals("OVERDUE", snapshot.workOrdersOverdue().get(0).detail().get("timeliness"));
        assertTrue(snapshot.workOrdersOverdue().get(0).attention());
        assertEquals(1, snapshot.workOrdersExecuting().size());
        assertEquals(snapshot.workOrdersExecuting().get(0).id(), snapshot.workOrdersOverdue().get(0).id());
        assertTrue(snapshot.workOrdersOtherOpen().isEmpty());
        assertEquals(1, snapshot.allRows().size());
    }

    @Test
    void overdueUsesTheEd36DeadlineRule() {
        // abertura ha 5h + deadline de 2h do tipo = vencida ha 3h (OrderMonitoring da ED-36)
        SituationSnapshot snapshot = snapshotWith(List.of(order(ServiceOrderStatus.OPEN, 2, null)));
        assertEquals(1, snapshot.workOrdersOverdue().size());
        assertEquals("OVERDUE", snapshot.workOrdersOverdue().get(0).detail().get("timeliness"));
        assertTrue(snapshot.workOrdersOverdue().get(0).attention());
    }

    @Test
    void orderWithoutAnyDeadlineIsUnknownNeverInvented() {
        SituationSnapshot snapshot = snapshotWith(List.of(order(ServiceOrderStatus.SCHEDULED, null, null)));
        assertEquals(1, snapshot.workOrdersOtherOpen().size());
        assertEquals("UNKNOWN", snapshot.workOrdersOtherOpen().get(0).detail().get("timeliness"));
    }

    @Test
    void terminalOrdersNeverEnterTheConsole() {
        SituationSnapshot snapshot = snapshotWith(List.of(
                order(ServiceOrderStatus.CLOSED_EXECUTED, 1, null),
                order(ServiceOrderStatus.CLOSED_NOT_EXECUTED, 1, null)));
        assertTrue(snapshot.allRows().isEmpty());
    }
}
