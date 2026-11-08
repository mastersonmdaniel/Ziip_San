package br.com.ziipsan.core.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Alcada (JOR-32): automatica registrada, controlado sempre humano, faixa/papel, autoaprovacao 403 + auditoria. */
class RequisitionApprovalTest {

    @Test
    void autoApprovalBelowLimitIsRecordedAndReserves() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "ENT-1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("10"))), T0); // 10 x 2,00 = 20,00 <= 100
        assertTrue(w.tryAutoApproval(TENANT, req.id(), T0));
        assertEquals(Requisition.Status.APPROVED, req.status());
        assertEquals(Requisition.ApprovalMode.AUTO, req.approvalMode());
        assertEquals(WarehouseService.SYSTEM_ACTOR, req.approverId());
        assertNotNull(req.approvalNote());
        var bal = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("20").compareTo(bal.physical()));   // reserva mantem o fisico
        assertEquals(0, bd("10").compareTo(bal.reserved()));
        assertEquals(0, bd("10").compareTo(bal.available()));
        assertTrue(store.audit(TENANT).stream().anyMatch(a -> a.action().equals("REQUISITION_AUTO_APPROVED")));
    }

    @Test
    void controlledItemNeverAutoApproved() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "VALVULA", LOC, bd("5"), bd("10.00"), "ENT-1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("VALVULA", bd("1"))), T0); // 10,00: abaixo do limite, mas CONTROLADO
        assertFalse(w.tryAutoApproval(TENANT, req.id(), T0));
        assertEquals(Requisition.Status.PENDING_APPROVAL, req.status());
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE"), T0); // humana funciona
        assertEquals(Requisition.ApprovalMode.HUMAN, req.approvalMode());
    }

    @Test
    void aboveAutoLimitNeedsBandRole() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("500"), bd("2.00"), "ENT-1", ACTOR, T0);
        Requisition big = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("300"))), T0); // 600,00 -> faixa GESTOR
        assertFalse(w.tryAutoApproval(TENANT, big.id(), T0));
        var denied = assertThrows(InventoryExceptions.AccessDeniedException.class,
                () -> w.approve(TENANT, big.id(), APPROVER, roles("ALMOXARIFE"), T0));
        assertEquals("APPROVER_ROLE_REQUIRED", denied.code());
        assertEquals(403, denied.httpStatus());
        w.approve(TENANT, big.id(), APPROVER, roles("GESTOR"), T0);
        assertEquals(Requisition.Status.APPROVED, big.status());
    }

    @Test
    void requesterCannotApproveOwnRequisition403AndAudit() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("50"), bd("2.00"), "ENT-1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("60"))), T0); // 120,00 -> humana
        var ex = assertThrows(InventoryExceptions.AccessDeniedException.class,
                () -> w.approve(TENANT, req.id(), REQUESTER, roles("GESTOR"), T0));
        assertEquals("SELF_APPROVAL_FORBIDDEN", ex.code());
        assertEquals(403, ex.httpStatus());
        assertEquals(Requisition.Status.PENDING_APPROVAL, req.status());
        assertTrue(store.audit(TENANT).stream().anyMatch(a -> a.denied() && a.action().equals("REQUISITION_APPROVAL_DENIED")));
    }

    @Test
    void missingParametersRefuseByName() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = new WarehouseService(store, noParams(), null);
        WarehouseService stockedW = stocked(store);
        stockedW.entry(TENANT, "CANO", LOC, bd("50"), bd("2.00"), "ENT-1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("60"))), T0);
        var ex = assertThrows(InventoryExceptions.ValidationException.class, () -> w.tryAutoApproval(TENANT, req.id(), T0));
        assertTrue(ex.getMessage().contains("inv.auto_approval_limit"));
        var ex2 = assertThrows(InventoryExceptions.ValidationException.class,
                () -> w.approve(TENANT, req.id(), APPROVER, roles("GESTOR"), T0));
        assertTrue(ex2.getMessage().contains("inv.approval_bands"));
    }

    @Test
    void tenantIsolationOnRequisitions() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("50"), bd("2.00"), "ENT-1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("1"))), T0);
        assertThrows(InventoryExceptions.NotFoundException.class,
                () -> w.approve(OTHER_TENANT, req.id(), APPROVER, roles("GESTOR"), T0));
        assertTrue(store.balances(OTHER_TENANT).isEmpty() || store.requisitionsByOrder(OTHER_TENANT, ORDER).isEmpty());
    }
}
