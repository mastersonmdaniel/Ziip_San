package br.com.ziipsan.core.inventory;

import br.com.ziipsan.core.fieldos.MaterialUse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** OPE-29/ED-20: baixa de campo idempotente por task_id; controlado com alcada a posteriori; piso zero. */
class FieldWriteOffIdempotencyTest {

    @Test
    void resendDoesNotWriteOffTwice() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        FieldWriteOffService field = new FieldWriteOffService(store, w);
        var lines = List.of(new MaterialUse.Line("CANO", bd("5")));
        var first = field.writeOffDetailed(TENANT, "TASK-1", lines, LOC, ACTOR, T0);
        assertFalse(first.duplicate());
        assertEquals(0, bd("15").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        var second = field.writeOffDetailed(TENANT, "TASK-1", lines, LOC, ACTOR, T0);
        assertTrue(second.duplicate());
        assertEquals(0, bd("15").compareTo(store.balance(TENANT, "CANO", LOC).physical())); // nao baixou de novo
        assertEquals(1, store.movements(TENANT).stream().filter(m -> m.type() == StockMovement.Type.FIELD_WRITE_OFF).count());
    }

    @Test
    void controlledFieldUseCreatesPosteriorApproval() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "VALVULA", LOC, bd("4"), bd("10.00"), "E1", ACTOR, T0);
        FieldWriteOffService field = new FieldWriteOffService(store, w);
        var result = field.writeOffDetailed(TENANT, "TASK-2", List.of(new MaterialUse.Line("VALVULA", bd("1"))), LOC, ACTOR, T0);
        assertTrue(result.lines().get(0).posteriorApprovalCreated());
        assertEquals(1, store.posteriorApprovals(TENANT).size());
        var approval = store.posteriorApprovals(TENANT).get(0);
        field.decidePosteriorApproval(TENANT, approval.id(), APPROVER, true, T0);
        assertEquals(InventoryStore.PosteriorApproval.Status.APPROVED, store.posteriorApprovals(TENANT).get(0).status());
        // evidencia por tarefa (ISO 45001/ED-71)
        assertEquals(1, InventoryReports.taskEvidence(store, TENANT, "TASK-2").size());
    }

    @Test
    void posteriorRejectionCreatesRegularizationPendencyNotUndo() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "VALVULA", LOC, bd("4"), bd("10.00"), "E1", ACTOR, T0);
        FieldWriteOffService field = new FieldWriteOffService(store, w);
        field.writeOffDetailed(TENANT, "TASK-3", List.of(new MaterialUse.Line("VALVULA", bd("1"))), LOC, ACTOR, T0);
        var approval = store.posteriorApprovals(TENANT).get(0);
        field.decidePosteriorApproval(TENANT, approval.id(), APPROVER, false, T0);
        assertEquals(0, bd("3").compareTo(store.balance(TENANT, "VALVULA", LOC).physical())); // baixa NAO desfeita
        assertEquals(1, store.pendencies(TENANT).size());
    }

    @Test
    void fieldWriteOffFloorsAtZeroWithShortfallPendency() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("2"), bd("2.00"), "E1", ACTOR, T0);
        FieldWriteOffService field = new FieldWriteOffService(store, w);
        var result = field.writeOffDetailed(TENANT, "TASK-4", List.of(new MaterialUse.Line("CANO", bd("5"))), LOC, ACTOR, T0);
        assertEquals(0, bd("0").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(0, bd("2").compareTo(result.lines().get(0).writtenQty()));
        assertEquals(0, bd("3").compareTo(result.lines().get(0).shortfallQty()));
        assertEquals(1, store.pendencies(TENANT).size());
    }
}
