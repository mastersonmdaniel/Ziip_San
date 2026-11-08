package br.com.ziipsan.core.inventory;

import br.com.ziipsan.core.fieldos.MaterialUse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Correcao ED-58 (revisao cruzada): captura M1-M4 da REVISAO-ED-58.
 * Cada teste FALHA no codigo pre-correcao e passa com a correcao.
 */
class CorrectionEd58Test {

    private Requisition approvedDelivered(WarehouseService w, String qty) {
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd(qty))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE", "GESTOR"), T0);
        w.deliver(TENANT, req.id(), ACTOR, T0);
        return req;
    }

    // ---------- M1: closeOrder agrega o usado UMA vez por item ----------
    @Test
    void m1CloseAggregatesUsedOncePerItemAcrossRequisitions() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("50"), bd("2.00"), "E1", ACTOR, T0);
        approvedDelivered(w, "5");
        approvedDelivered(w, "5"); // fisico 40, entregues 10 no total
        WarehouseService.CloseResult close = w.closeOrder(TENANT, ORDER, Map.of("CANO", bd("7")), ACTOR, T0);
        // esperado do revisor (S5c): fisico 43 (40 + sobra 3), custo 14,00 (7 x 2,00)
        assertEquals(0, bd("43").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(0, bd("14.00").compareTo(close.totalMaterialCost()));
        BigDecimal returned = close.lines().stream().map(WarehouseService.CloseLine::returnedQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertEquals(0, bd("3").compareTo(returned));
    }

    @Test
    void m1CloseUsedAboveDeliveredAggregatedAcrossRequisitions() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("50"), bd("2.00"), "E1", ACTOR, T0);
        approvedDelivered(w, "5");
        approvedDelivered(w, "5"); // fisico 40, entregues 10
        WarehouseService.CloseResult close = w.closeOrder(TENANT, ORDER, Map.of("CANO", bd("13")), ACTOR, T0);
        // usado 13: extra agregado = 3 (uma unica vez), fisico 37, custo 26,00, sem falta
        assertEquals(0, bd("37").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(0, bd("26.00").compareTo(close.totalMaterialCost()));
        BigDecimal extra = close.lines().stream().map(WarehouseService.CloseLine::extraWrittenQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        BigDecimal shortfall = close.lines().stream().map(WarehouseService.CloseLine::shortfallQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertEquals(0, bd("3").compareTo(extra));
        assertEquals(0, bd("0").compareTo(shortfall));
        assertEquals(0, store.pendencies(TENANT).size());
    }

    // ---------- M2: baixa de campo atomica + reenvio idempotente ----------
    @Test
    void m2FieldWriteOffValidatesAllLinesBeforeAnyWriteOff() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        w.entry(TENANT, "VALVULA", LOC, bd("4"), bd("10.00"), "E1", ACTOR, T0);
        FieldWriteOffService field = new FieldWriteOffService(store, w);
        // MaterialUse.Line permite qty 0 (ED-20 real): a operacao INTEIRA e recusada, nada baixa
        var ex = assertThrows(InventoryExceptions.ValidationException.class, () ->
                field.writeOffDetailed(TENANT, "TASK-Z", List.of(
                        new MaterialUse.Line("CANO", bd("5")),
                        new MaterialUse.Line("VALVULA", bd("0"))), LOC, ACTOR, T0));
        assertEquals("FIELD_LINE_QTY_INVALID", ex.code());
        assertEquals(0, bd("20").compareTo(store.balance(TENANT, "CANO", LOC).physical())); // nada baixou
        assertEquals(0, store.movements(TENANT).stream()
                .filter(m -> m.type() == StockMovement.Type.FIELD_WRITE_OFF).count());
        assertFalse(store.fieldTaskProcessed(TENANT, "TASK-Z")); // tarefa NAO marcada
        // reenvio VALIDO da mesma tarefa: processa UMA vez (nao duplica a parte ja "baixada")
        var retry = field.writeOffDetailed(TENANT, "TASK-Z",
                List.of(new MaterialUse.Line("CANO", bd("5"))), LOC, ACTOR, T0);
        assertFalse(retry.duplicate());
        assertEquals(0, bd("15").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        // e o reenvio do reenvio segue idempotente por task_id
        var again = field.writeOffDetailed(TENANT, "TASK-Z",
                List.of(new MaterialUse.Line("CANO", bd("5"))), LOC, ACTOR, T0);
        assertTrue(again.duplicate());
        assertEquals(0, bd("15").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(1, store.movements(TENANT).stream()
                .filter(m -> m.type() == StockMovement.Type.FIELD_WRITE_OFF).count());
    }

    // ---------- M3: NF-e valida antes de postar + idempotente por chave ----------
    static final UUID SUPPLIER = UUID.fromString("00000000-0000-0000-0000-000000005830");
    static InventoryPorts.SupplierLookup suppliers() {
        return new InventoryPorts.SupplierLookup() {
            public Optional<UUID> findSupplierIdByDocument(UUID t, String doc) {
                return "12345678000199".equals(doc) ? Optional.of(SUPPLIER) : Optional.empty();
            }
            public boolean isActive(UUID t, UUID id) { return SUPPLIER.equals(id); }
        };
    }
    static String xmlCloro(String key, String rastro) {
        return "<nfeProc><NFe><infNFe Id=\"" + key + "\"><emit><CNPJ>12345678000199</CNPJ></emit>"
                + "<det nItem=\"1\"><prod><cProd>CLORO</cProd><xProd>Cloro</xProd><qCom>5.0000</qCom>"
                + "<vUnCom>4.00</vUnCom></prod>" + rastro + "</det></infNFe></NFe></nfeProc>";
    }
    static String xmlCano(String key) {
        return "<nfeProc><NFe><infNFe Id=\"" + key + "\"><emit><CNPJ>12345678000199</CNPJ></emit>"
                + "<det nItem=\"1\"><prod><cProd>CANO</cProd><xProd>Cano</xProd><qCom>10.0000</qCom>"
                + "<vUnCom>3.00</vUnCom></prod></det></infNFe></NFe></nfeProc>";
    }
    private WarehouseService nfeService(InMemoryInventoryStore store) {
        WarehouseService w = stocked(store);
        w.registerItem(TENANT, new InventoryItem.Item("CLORO", "Cloro", InventoryItem.Kind.REAGENT, "kg",
                false, false, true, null, true), ACTOR, T0);
        return new WarehouseService(store, params(), suppliers());
    }

    @Test
    void m3LotWithoutExpiryRefusedBeforePosting() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = nfeService(store);
        String xml = xmlCloro("NFe3527", "<rastro><nLote>L-9</nLote></rastro>"); // nLote SEM dVal
        var ex = assertThrows(InventoryExceptions.ValidationException.class, () ->
                w.receiveFromNfe(TENANT, xml, Map.of("CLORO", bd("5")), LOC, ACTOR, T0));
        assertEquals("NFE_LOT_EXPIRY_MISSING", ex.code()); // recusa NOMEADA, nao NPE
        assertEquals(0, bd("0").compareTo(store.balance(TENANT, "CLORO", LOC).physical())); // fisico intacto
        assertEquals(0, store.lots(TENANT).size());
        assertEquals(0, store.movements(TENANT).stream()
                .filter(m -> m.type() == StockMovement.Type.ENTRY).count());
        // a mesma chave, corrigida (com dVal), posta normalmente: a recusa nao queimou a chave
        String fixed = xmlCloro("NFe3527", "<rastro><nLote>L-9</nLote><dVal>2027-01-31</dVal></rastro>");
        var ok = w.receiveFromNfe(TENANT, fixed, Map.of("CLORO", bd("5")), LOC, ACTOR, T0);
        assertTrue(ok.posted());
        assertEquals(0, bd("5").compareTo(store.balance(TENANT, "CLORO", LOC).physical()));
        assertEquals(1, store.lots(TENANT).size());
    }

    @Test
    void m3ResendOfSameNfeDoesNotDuplicateEntry() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = nfeService(store);
        String xml = xmlCano("NFe3526111");
        var first = w.receiveFromNfe(TENANT, xml, Map.of("CANO", bd("10")), LOC, ACTOR, T0);
        assertTrue(first.posted());
        assertEquals(0, bd("10").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        var second = w.receiveFromNfe(TENANT, xml, Map.of("CANO", bd("10")), LOC, ACTOR, T0);
        assertFalse(second.posted()); // reenvio idempotente por chave: esta chamada NAO posta de novo
        assertEquals(0, bd("10").compareTo(store.balance(TENANT, "CANO", LOC).physical())); // nao duplicou
        assertEquals(1, store.movements(TENANT).stream()
                .filter(m -> m.type() == StockMovement.Type.ENTRY).count());
    }

    @Test
    void m3DivergentNfeDoesNotBurnAccessKey() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = nfeService(store);
        String xml = xmlCano("NFe3526222");
        var div = w.receiveFromNfe(TENANT, xml, Map.of("CANO", bd("8")), LOC, ACTOR, T0);
        assertFalse(div.posted());
        var ok = w.receiveFromNfe(TENANT, xml, Map.of("CANO", bd("10")), LOC, ACTOR, T0);
        assertTrue(ok.posted()); // divergencia nao marca a chave como processada
        assertEquals(0, bd("10").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
    }

    // ---------- M4: cancelOrder pre-valida TODAS antes de mutar ----------
    @Test
    void m4MixedOrderCancelRefusedWithoutAnyMutation() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("50"), bd("2.00"), "E1", ACTOR, T0);
        Requisition rA = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("5"))), T0);
        w.approve(TENANT, rA.id(), APPROVER, roles("ALMOXARIFE"), T0); // APPROVED (criada antes)
        Requisition rB = approvedDelivered(w, "5"); // DELIVERED
        // estado antes: fisico 45, reservado 5 (so rA)
        assertEquals(0, bd("45").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(0, bd("5").compareTo(store.balance(TENANT, "CANO", LOC).reserved()));
        var ex = assertThrows(InventoryExceptions.ValidationException.class, () ->
                w.cancelOrder(TENANT, ORDER, ACTOR, T0));
        assertEquals("ORDER_ALREADY_DELIVERED", ex.code());
        // NADA mudou: rA segue APPROVED com a reserva, rB DELIVERED, sem liberacao nem auditoria de cancelamento
        assertEquals(Requisition.Status.APPROVED, rA.status());
        assertEquals(Requisition.Status.DELIVERED, rB.status());
        assertEquals(0, bd("5").compareTo(store.balance(TENANT, "CANO", LOC).reserved()));
        assertEquals(0, bd("45").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertEquals(0, store.movements(TENANT).stream()
                .filter(m -> m.type() == StockMovement.Type.RESERVATION_RELEASE).count());
        assertEquals(0, store.audit(TENANT).stream()
                .filter(a -> a.action().equals("ORDER_CANCELLED_STOCK")).count());
    }

    @Test
    void m4ValidCancelReleasesReservationAndAudits() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService w = stocked(store);
        w.entry(TENANT, "CANO", LOC, bd("20"), bd("2.00"), "E1", ACTOR, T0);
        Requisition req = w.createRequisition(TENANT, ORDER, REQUESTER, LOC,
                List.of(new WarehouseService.RequisitionLineInput("CANO", bd("8"))), T0);
        w.approve(TENANT, req.id(), APPROVER, roles("ALMOXARIFE"), T0);
        w.cancelOrder(TENANT, ORDER, ACTOR, T0);
        assertEquals(Requisition.Status.CANCELLED, req.status());
        assertEquals(0, bd("0").compareTo(store.balance(TENANT, "CANO", LOC).reserved()));
        assertTrue(store.audit(TENANT).stream()
                .anyMatch(a -> a.action().equals("ORDER_CANCELLED_STOCK"))); // cancelamento valido grava auditoria
    }
}
