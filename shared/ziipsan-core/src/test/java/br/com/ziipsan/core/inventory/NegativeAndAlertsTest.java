package br.com.ziipsan.core.inventory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Negativos do aceite: aprovacao sem saldo, relatorio diario, prazo de regularizacao; baixa automatica com piso zero. */
class NegativeAndAlertsTest {

    @Test
    void approvalWithoutStockLeavesNegativeAvailableAndAlerts() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("3"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("10"))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE"), T0);
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("3").compareTo(b.physical()));   // fisico intocado na reserva
        assertEquals(0, bd("-7").compareTo(b.available())); // saldo (disponivel) negativo
        assertTrue(store.alerts(TENANT).stream().anyMatch(a -> a.kind() == InventoryPorts.AlertKind.NEGATIVE_BALANCE));
        var negatives = InventoryReports.dailyNegatives(store, TENANT, T0, 5);
        assertEquals(1, negatives.size());
        assertFalse(negatives.get(0).overdue());
        // Central ve o mesmo negativo
        var central = InventoryReports.centralView(store, params(), TENANT, T0);
        assertEquals(1, central.negatives().size());
        assertFalse(central.alerts().isEmpty());
    }

    @Test
    void dailyNegativesFlagOverdueAfterRegularizationDays() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("3"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("10"))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE"), T0);
        Instant later = Instant.parse("2026-11-16T10:00:00Z"); // 7 dias > prazo de 5
        var negatives = InventoryReports.dailyNegatives(store, TENANT, later, 5);
        assertEquals(1, negatives.size());
        assertEquals(7, negatives.get(0).daysNegative());
        assertTrue(negatives.get(0).overdue());
    }

    @Test
    void automaticClosureWriteOffNeverDrivesPhysicalNegative() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("12"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("10"))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE"), T0);
        w.deliver(TENANT, req.id(), ACTOR, T0); // fisico 2
        WarehouseService.CloseResult close = w.closeOrder(TENANT, ORDER, Map.of("CANO", bd("15")), ACTOR, T0);
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("0").compareTo(b.physical())); // piso zero: nunca negativo
        assertEquals(0, bd("2").compareTo(close.lines().get(0).extraWrittenQty()));
        assertEquals(0, bd("3").compareTo(close.lines().get(0).shortfallQty())); // 15-10-2
        assertEquals(1, store.pendencies(TENANT).size());
        assertTrue(store.alerts(TENANT).stream().anyMatch(a -> a.kind() == InventoryPorts.AlertKind.WRITE_OFF_SHORTFALL));
    }

    @Test
    void reorderPointAlertFromItemDeclaration() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("6"), bd("2.00"), "E1", ACTOR, T0); // ponto de reposicao do CANO = 5
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("2"))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE"), T0); // disponivel 4 <= 5
        assertTrue(store.alerts(TENANT).stream().anyMatch(a -> a.kind() == InventoryPorts.AlertKind.REORDER_POINT));
        assertEquals(1, InventoryReports.atOrBelowReorderPoint(store, TENANT).size());
    }
}
