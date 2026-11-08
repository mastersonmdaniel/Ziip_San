package br.com.ziipsan.core.inventory;

import br.com.ziipsan.core.backoffice.PlannedMaterials;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** SG-476 kit; custo medio ponderado; inventario com justificativa; relatorio de materiais; auditoria total. */
class KitAverageCostInventoryTest {

    @Test
    void kitPrefillsPlannedMaterialsOfOrderOpening() {
        MaterialKit.Kit kit = new MaterialKit.Kit(TENANT, "LIGACAO_NOVA", List.of(
                new MaterialKit.KitLine("CANO", bd("6")), new MaterialKit.KitLine("HIDRO", bd("1"))));
        List<PlannedMaterials.Line> planned = MaterialKit.prefillPlanned(kit);
        PlannedMaterials pm = new PlannedMaterials(planned); // o construtor REAL da ED-36 aceita (usado nulo)
        assertEquals(2, pm.lines().size());
        assertEquals(2, pm.unrecordedLines().size());
        assertEquals(0, bd("7").compareTo(pm.plannedTotalQty()));
    }

    @Test
    void weightedAverageCostAcrossEntries() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("10"), bd("2.00"), "E1", ACTOR, T0);
        w.entry(TENANT, "CANO", LOC, bd("10"), bd("4.00"), "E2", ACTOR, T0);
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("3.0000").compareTo(b.averageCost())); // (10x2 + 10x4)/20
        assertEquals(0, bd("60.00").compareTo(b.stockValue()));
        var report = InventoryReports.materialsReport(store, TENANT);
        assertEquals(1, report.size());
        assertEquals(0, bd("20").compareTo(report.get(0).physical()));
    }

    @Test
    void inventoryDivergenceRequiresJustificationAndAdjusts() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        assertThrows(InventoryExceptions.ValidationException.class,
                () -> w.inventoryCount(TENANT, LOC, Map.of("CANO", bd("18")), null, ACTOR, T0));
        assertThrows(InventoryExceptions.ValidationException.class,
                () -> w.adjust(TENANT, "CANO", LOC, bd("-1"), " ", ACTOR, T0));
        var count = w.inventoryCount(TENANT, LOC, Map.of("CANO", bd("18")), "quebra conferida em campo", ACTOR, T0);
        assertEquals(0, bd("-2").compareTo(count.lines().get(0).difference()));
        assertEquals(0, bd("18").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
    }

    @Test
    void everyOperationIsAudited() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("5"))), T0);
        assertTrue(w.tryAutoApproval(TENANT, req.id(), T0));
        w.deliver(TENANT, req.id(), ACTOR, T0);
        w.closeOrder(TENANT, ORDER, Map.of("CANO", bd("5")), ACTOR, T0);
        var actions = store.audit(TENANT).stream().map(InventoryPorts.AuditEntry::action).toList();
        for (String expected : List.of("STOCK_ENTRY", "REQUISITION_CREATED", "REQUISITION_AUTO_APPROVED",
                "REQUISITION_DELIVERED", "ORDER_CLOSED_STOCK"))
            assertTrue(actions.contains(expected), "auditoria sem " + expected + ": " + actions);
        assertFalse(store.movements(TENANT).isEmpty());
    }
}
