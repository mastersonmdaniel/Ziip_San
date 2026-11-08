package br.com.ziipsan.core.chemicals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static br.com.ziipsan.core.chemicals.ChemTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ChemProductReceiptTest {
    static final Instant AT = Instant.parse("2026-03-02T12:00:00Z");

    private ChemService service(InMemoryChemStore store, RecordingStock stock) {
        ChemService svc = new ChemService(store, new TestParams(), stock);
        svc.registerProduct(product(TENANT, "CLORO", "Cloro granulado"), chemItem("CLORO", "Cloro granulado"), ACTOR, AT);
        return svc;
    }

    private ChemReceipt.LotReceipt lot(String code, String lot, String expiry, String qty) {
        return new ChemReceipt.LotReceipt(TENANT, code, "ALM-01", lot, LocalDate.parse(expiry),
                "LAUDO-" + lot, new BigDecimal(qty), AT);
    }

    @Test void productRequiresSdsAndChemicalLotTrackedItem() {
        assertThrows(ChemExceptions.ValidationException.class, () ->
                new ChemProduct(TENANT, "X", "X", ChemProduct.StorageKind.SILO, "ALM-01", " ", LocalDate.of(2026, 1, 1), true));
        var svc = new ChemService(new InMemoryChemStore(), new TestParams(), new RecordingStock());
        var reagent = new br.com.ziipsan.core.inventory.InventoryItem.Item("CLORO", "Cloro",
                br.com.ziipsan.core.inventory.InventoryItem.Kind.REAGENT, "KG", false, false, true, null, true);
        assertThrows(ChemExceptions.ValidationException.class, () ->
                svc.registerProduct(product(TENANT, "CLORO", "Cloro granulado"), reagent, ACTOR, AT));
    }

    @Test void receiptPostsEntryAndLotWhenConferred() {
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var svc = service(store, stock);
        var result = svc.receiveFromNfe(TENANT, nfeXml("NFe123", "12345678000199", "CLORO", "100", "5.50"),
                Map.of("CLORO", new BigDecimal("100")),
                Map.of("CLORO", lot("CLORO", "L1", "2027-01-31", "100")), ACTOR, AT);
        assertFalse(result.duplicate());
        assertEquals(1, stock.entries.size());
        assertEquals(new BigDecimal("100"), stock.entries.get(0).qty());
        assertEquals(1, store.lots(TENANT, "CLORO").size());
        assertEquals("LAUDO-L1", store.lots(TENANT, "CLORO").get(0).supplierCertificateRef());
    }

    @Test void receiptPostsEachLotInItsDeclaredLocation() {
        // R3: NF-e multi-local — cada lote posta no SEU locationCode
        // declarado, nao no local do primeiro produto da nota.
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var svc = service(store, stock);
        svc.registerProduct(product(TENANT, "ACIDO", "Acido de teste"), chemItem("ACIDO", "Acido de teste"), ACTOR, AT);
        String xml = "<nfeProc><NFe><infNFe Id=\"NFeM1\">"
                + "<emit><CNPJ>12345678000199</CNPJ></emit>"
                + "<det><prod><cProd>CLORO</cProd><xProd>Produto CLORO</xProd>"
                + "<qCom>100</qCom><vUnCom>5.50</vUnCom></prod></det>"
                + "<det><prod><cProd>ACIDO</cProd><xProd>Produto ACIDO</xProd>"
                + "<qCom>10</qCom><vUnCom>9.90</vUnCom></prod></det>"
                + "</infNFe></NFe></nfeProc>";
        var lots = Map.of(
                "CLORO", new ChemReceipt.LotReceipt(TENANT, "CLORO", "ALM-01", "LM1",
                        LocalDate.of(2027, 1, 31), "LAUDO-LM1", new BigDecimal("100"), AT),
                "ACIDO", new ChemReceipt.LotReceipt(TENANT, "ACIDO", "TANQ-02", "LM2",
                        LocalDate.of(2027, 1, 31), "LAUDO-LM2", new BigDecimal("10"), AT));
        var result = svc.receiveFromNfe(TENANT, xml,
                Map.of("CLORO", new BigDecimal("100"), "ACIDO", new BigDecimal("10")), lots, ACTOR, AT);
        assertFalse(result.duplicate());
        assertEquals(2, stock.entries.size());
        var byItem = new java.util.HashMap<String, String>();
        for (var e : stock.entries) byItem.put(e.item(), e.location());
        assertEquals("ALM-01", byItem.get("CLORO"));
        assertEquals("TANQ-02", byItem.get("ACIDO"));
        assertEquals("ALM-01", store.lots(TENANT, "CLORO").get(0).locationCode());
        assertEquals("TANQ-02", store.lots(TENANT, "ACIDO").get(0).locationCode());
    }

    @Test void receiptIsIdempotentByAccessKey() {
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var svc = service(store, stock);
        String xml = nfeXml("NFe999", "12345678000199", "CLORO", "10", "5.50");
        var lots = Map.of("CLORO", lot("CLORO", "L9", "2027-01-31", "10"));
        svc.receiveFromNfe(TENANT, xml, Map.of("CLORO", new BigDecimal("10")), lots, ACTOR, AT);
        var again = svc.receiveFromNfe(TENANT, xml, Map.of("CLORO", new BigDecimal("10")), lots, ACTOR, AT);
        assertTrue(again.duplicate());
        assertEquals(1, stock.entries.size());
    }

    @Test void divergencePostsNothing() {
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var svc = service(store, stock);
        assertThrows(ChemExceptions.ValidationException.class, () ->
                svc.receiveFromNfe(TENANT, nfeXml("NFe124", "12345678000199", "CLORO", "90", "5.50"),
                        Map.of("CLORO", new BigDecimal("100")),
                        Map.of("CLORO", lot("CLORO", "L2", "2027-01-31", "90")), ACTOR, AT));
        assertEquals(0, stock.entries.size());
        assertEquals(0, store.lots(TENANT, "CLORO").size());
    }

    @Test void missingCertificateAndExpiredLotRefused() {
        assertThrows(ChemExceptions.ValidationException.class, () ->
                new ChemReceipt.LotReceipt(TENANT, "CLORO", "ALM-01", "L3", LocalDate.of(2027, 1, 1), " ", BigDecimal.TEN, AT));
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var svc = service(store, stock);
        assertThrows(ChemExceptions.ValidationException.class, () ->
                svc.receiveFromNfe(TENANT, nfeXml("NFe125", "12345678000199", "CLORO", "10", "5.50"),
                        Map.of("CLORO", new BigDecimal("10")),
                        Map.of("CLORO", lot("CLORO", "L4", "2026-03-01", "10")), ACTOR, AT));
        assertEquals(0, stock.entries.size());
    }

    @Test void tenantIsolationOnProducts() {
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        service(store, stock);
        assertTrue(store.product(TENANT_B, "CLORO").isEmpty());
        assertEquals(0, store.lots(TENANT_B, "CLORO").size());
    }
}
