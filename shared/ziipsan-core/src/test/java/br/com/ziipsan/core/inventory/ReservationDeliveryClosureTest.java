package br.com.ziipsan.core.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** JOR-36: reserva x fisico, entrega, encerramento (baixa usado + devolve sobra + custo), cancelamento libera. */
class ReservationDeliveryClosureTest {

    private Requisition approvedReq(WarehouseService w, String item, String qty) {
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput(item, bd(qty))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE", "GESTOR"), T0);
        return req;
    }

    @Test
    void reservationReducesAvailableKeepsPhysical() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        approvedReq(w, "CANO", "8");
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("20").compareTo(b.physical()));
        assertEquals(0, bd("8").compareTo(b.reserved()));
        assertEquals(0, bd("12").compareTo(b.available()));
    }

    @Test
    void closureWritesOffUsedReturnsLeftoverAndAppropriatesCost() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = approvedReq(w, "CANO", "10");
        w.deliver(TENANT, req.id(), ACTOR, T0);
        var afterDelivery = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("10").compareTo(afterDelivery.physical()));
        assertEquals(0, bd("0").compareTo(afterDelivery.reserved()));
        WarehouseService.CloseResult close = w.closeOrder(TENANT, ORDER, Map.of("CANO", bd("7")), ACTOR, T0);
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("13").compareTo(b.physical())); // 10 restantes + 3 de sobra devolvida
        assertEquals(0, bd("14.00").compareTo(close.totalMaterialCost())); // 7 x 2,00 apropriado na OS
        assertEquals(0, bd("3").compareTo(close.lines().get(0).returnedQty()));
        assertEquals(Requisition.Status.CLOSED, req.status());
    }

    @Test
    void closureUsedAboveDeliveredWritesOffFromPhysicalWithFloorAndCost() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = approvedReq(w, "CANO", "10");
        w.deliver(TENANT, req.id(), ACTOR, T0); // fisico 10
        WarehouseService.CloseResult close = w.closeOrder(TENANT, ORDER, Map.of("CANO", bd("13")), ACTOR, T0);
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("7").compareTo(b.physical())); // 10 - 3 extras
        assertEquals(0, bd("3").compareTo(close.lines().get(0).extraWrittenQty()));
        assertEquals(0, bd("0").compareTo(close.lines().get(0).shortfallQty()));
        assertEquals(0, bd("26.00").compareTo(close.totalMaterialCost())); // 13 x 2,00
    }

    @Test
    void cancelledOrderReleasesReservation() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = approvedReq(w, "CANO", "8");
        w.cancelOrder(TENANT, ORDER, ACTOR, T0);
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("0").compareTo(b.reserved()));
        assertEquals(0, bd("20").compareTo(b.available()));
        assertEquals(Requisition.Status.CANCELLED, req.status());
    }

    @Test
    void closureRequiresUsedForEveryDeliveredLine() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = approvedReq(w, "CANO", "5");
        w.deliver(TENANT, req.id(), ACTOR, T0);
        assertThrows(InventoryExceptions.ValidationException.class,
                () -> w.closeOrder(TENANT, ORDER, Map.of(), ACTOR, T0));
    }
}
