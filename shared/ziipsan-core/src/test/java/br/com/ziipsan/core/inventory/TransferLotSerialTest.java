package br.com.ziipsan.core.inventory;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Transferencia em transito; lote/validade/CA; hidrometro serie+lacre ate a sucata. */
class TransferLotSerialTest {

    @Test
    void transferHasInTransitStateAndConfirmMovesStock() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        var tr = w.startTransfer(TENANT, "CANO", bd("6"), LOC, LOC2, ACTOR, T0);
        assertEquals(InventoryStore.Transfer.Status.IN_TRANSIT, tr.status());
        assertEquals(0, bd("14").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(0, bd("0").compareTo(store.balance(TENANT, "CANO", LOC2).physical())); // ainda em transito
        w.confirmTransfer(TENANT, tr.id(), ACTOR, T0);
        assertEquals(0, bd("6").compareTo(store.balance(TENANT, "CANO", LOC2).physical()));
        assertEquals(InventoryStore.Transfer.Status.RECEIVED, store.transfer(TENANT, tr.id()).orElseThrow().status());
    }

    @Test
    void transferCancelReturnsAndInsufficientIsRefused() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("5"), bd("2.00"), "E1", ACTOR, T0);
        assertThrows(InventoryExceptions.ValidationException.class,
                () -> w.startTransfer(TENANT, "CANO", bd("9"), LOC, LOC2, ACTOR, T0));
        var tr = w.startTransfer(TENANT, "CANO", bd("5"), LOC, LOC2, ACTOR, T0);
        w.cancelTransfer(TENANT, tr.id(), ACTOR, T0);
        assertEquals(0, bd("5").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
    }

    @Test
    void ppeLotRequiresCaAndExpiryAlertUsesTenantWindow() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        var noCa = new LotControl.Lot(TENANT, "LUVA", LOC, "L-1", LocalDate.of(2026, 12, 1), null, bd("10"));
        assertThrows(InventoryExceptions.ValidationException.class, () -> w.registerLot(TENANT, noCa, ACTOR, T0));
        w.registerLot(TENANT, new LotControl.Lot(TENANT, "LUVA", LOC, "L-1", LocalDate.of(2026, 12, 1), "CA-12345", bd("10")), ACTOR, T0);
        w.registerLot(TENANT, new LotControl.Lot(TENANT, "LUVA", LOC, "L-2", LocalDate.of(2027, 6, 1), "CA-12345", bd("10")), ACTOR, T0);
        var alerts = InventoryReports.lotExpiryAlerts(store, params(), TENANT, LocalDate.of(2026, 11, 9));
        assertEquals(1, alerts.size()); // so L-1 (22 dias <= janela de 30)
        assertEquals("L-1", alerts.get(0).lotCode());
    }

    @Test
    void meterSerialAndSealTrackedFromReceiptToScrap() {
        SerializedMeterTracking tracking = new SerializedMeterTracking();
        var unit = tracking.receive(TENANT, "SERIE-001", "HIDRO", "LACRE-9", LOC, ACTOR, T0);
        assertEquals(SerializedMeterTracking.Custody.STORED, unit.custody());
        tracking.transfer(TENANT, "SERIE-001", LOC2, ACTOR, T0);
        tracking.issueToOrder(TENANT, "SERIE-001", ORDER, ACTOR, T0);
        tracking.changeSeal(TENANT, "SERIE-001", "LACRE-10", "lacre rompido na instalacao", ACTOR, T0);
        assertThrows(IllegalArgumentException.class, () -> tracking.scrap(TENANT, "SERIE-001", " ", ACTOR, T0));
        tracking.scrap(TENANT, "SERIE-001", "visor trincado", ACTOR, T0);
        var done = tracking.unit(TENANT, "SERIE-001");
        assertEquals(SerializedMeterTracking.Custody.SCRAPPED, done.custody());
        assertEquals("LACRE-10", done.sealCode());
        // cadeia completa: recebimento, transferencia (2 eventos), entrega, troca de lacre, sucata
        assertEquals(6, done.history().size());
        assertEquals(SerializedMeterTracking.EventType.RECEIPT, done.history().get(0).type());
        assertEquals(SerializedMeterTracking.EventType.SCRAP, done.history().get(5).type());
        assertThrows(IllegalStateException.class, () -> tracking.transfer(TENANT, "SERIE-001", LOC, ACTOR, T0));
    }
}
