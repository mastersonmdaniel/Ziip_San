package br.com.ziipsan.core.inventory;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.core.inventory.InventoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** NF-e (P3.31): leitura do XML, conferencia e divergencia sem postar entrada. */
class NfeEntryTest {

    static final UUID SUPPLIER = UUID.fromString("00000000-0000-0000-0000-000000005830");
    static InventoryPorts.SupplierLookup suppliers() {
        return new InventoryPorts.SupplierLookup() {
            public Optional<UUID> findSupplierIdByDocument(UUID t, String doc) {
                return "12345678000199".equals(doc) ? Optional.of(SUPPLIER) : Optional.empty();
            }
            public boolean isActive(UUID t, UUID id) { return SUPPLIER.equals(id); }
        };
    }
    static String xml(String qtyCano) {
        return "<nfeProc><NFe><infNFe Id=\"NFe35261112345678000199550010000000011000000010\">"
                + "<emit><CNPJ>12345678000199</CNPJ><xNome>Fornecedor Sintetico</xNome></emit>"
                + "<det nItem=\"1\"><prod><cProd>CANO</cProd><xProd>Cano PVC</xProd><qCom>" + qtyCano
                + "</qCom><vUnCom>3.00</vUnCom><vProd>30.00</vProd></prod></det>"
                + "</infNFe></NFe></nfeProc>";
    }

    @Test
    void parseExtractsSupplierItemsAndValues() {
        var nfe = NfeEntry.parse(xml("10.0000"));
        assertEquals("12345678000199", nfe.supplierDocument());
        assertEquals(1, nfe.items().size());
        assertEquals("CANO", nfe.items().get(0).productCode());
        assertEquals(0, bd("10").compareTo(nfe.items().get(0).qty()));
        assertEquals(0, bd("3.00").compareTo(nfe.items().get(0).unitValue()));
    }

    @Test
    void conferredNfePostsEntryWithXmlCost() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        WarehouseService stockedW = stocked(store);
        WarehouseService w = new WarehouseService(store, params(), suppliers());
        var result = w.receiveFromNfe(TENANT, xml("10.0000"), Map.of("CANO", bd("10")), LOC, ACTOR, T0);
        assertTrue(result.posted());
        assertEquals(SUPPLIER, result.supplierId());
        var b = store.balance(TENANT, "CANO", LOC);
        assertEquals(0, bd("10").compareTo(b.physical()));
        assertEquals(0, bd("3.0000").compareTo(b.averageCost()));
    }

    @Test
    void divergentNfeDoesNotPostAndReports() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        stocked(store);
        WarehouseService w = new WarehouseService(store, params(), suppliers());
        var result = w.receiveFromNfe(TENANT, xml("8.0000"), Map.of("CANO", bd("10")), LOC, ACTOR, T0);
        assertFalse(result.posted());
        assertEquals(1, result.conference().divergences().size());
        assertEquals(NfeEntry.LineStatus.QTY_DIVERGENT, result.conference().divergences().get(0).status());
        assertEquals(0, bd("0").compareTo(store.balance(TENANT, "CANO", LOC).physical()));
        assertTrue(store.alerts(TENANT).stream().anyMatch(a -> a.kind() == InventoryPorts.AlertKind.NFE_DIVERGENCE));
    }

    @Test
    void unknownSupplierAndUnexpectedItemAreRefused() {
        InMemoryInventoryStore store = new InMemoryInventoryStore();
        stocked(store);
        WarehouseService w = new WarehouseService(store, params(), suppliers());
        String unknownSupplier = xml("10.0000").replace("12345678000199", "99999999000100");
        assertThrows(InventoryExceptions.ValidationException.class,
                () -> w.receiveFromNfe(TENANT, unknownSupplier, Map.of("CANO", bd("10")), LOC, ACTOR, T0));
        var unexpected = w.receiveFromNfe(TENANT, xml("10.0000"), Map.of(), LOC, ACTOR, T0);
        assertFalse(unexpected.posted());
        assertEquals(NfeEntry.LineStatus.UNEXPECTED_ITEM, unexpected.conference().divergences().get(0).status());
    }

    @Test
    void doctypeIsRefusedXxeHardening() {
        assertThrows(IllegalArgumentException.class,
                () -> NfeEntry.parse("<?xml version=\"1.0\"?><!DOCTYPE nfe [<!ENTITY x \"y\">]><nfeProc/>"));
    }
}
