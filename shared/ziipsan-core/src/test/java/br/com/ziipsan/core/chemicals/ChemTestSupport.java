package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.inventory.InventoryItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fixtures sinteticas da ED-89 (massa declarada; nenhum valor regulatorio real). */
final class ChemTestSupport {
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333333");

    static InventoryItem.Item chemItem(String code, String name) {
        return new InventoryItem.Item(code, name, InventoryItem.Kind.CHEMICAL, "KG",
                false, false, true, null, true);
    }

    static ChemProduct product(UUID tenant, String code, String name) {
        return new ChemProduct(tenant, code, name, ChemProduct.StorageKind.TANK, "ALM-01",
                "FDS-" + code + ".pdf", LocalDate.of(2026, 1, 10), true);
    }

    static String nfeXml(String accessKey, String cnpj, String code, String qty, String unitValue) {
        return "<nfeProc><NFe><infNFe Id=\"" + accessKey + "\">"
                + "<emit><CNPJ>" + cnpj + "</CNPJ></emit>"
                + "<det><prod><cProd>" + code + "</cProd><xProd>Produto " + code + "</xProd>"
                + "<qCom>" + qty + "</qCom><vUnCom>" + unitValue + "</vUnCom></prod></det>"
                + "</infNFe></NFe></nfeProc>";
    }

    /** Parametros em memoria: null = chave ausente. */
    static final class TestParams implements ChemParameters {
        Integer minStockDays; String listRef; Integer intervalMonths; Boolean pfEnabled; BigDecimal tolerancePct;
        public Optional<Integer> minStockDays(UUID t) { return Optional.ofNullable(minStockDays); }
        public Optional<String> controlledProductsListRef(UUID t) { return Optional.ofNullable(listRef); }
        public Optional<Integer> reservoirCleaningIntervalMonths(UUID t) { return Optional.ofNullable(intervalMonths); }
        public Optional<Boolean> pfControlEnabled(UUID t) { return Optional.ofNullable(pfEnabled); }
        public Optional<BigDecimal> reconciliationTolerancePct(UUID t) { return Optional.ofNullable(tolerancePct); }
    }

    /** Porta de entrada fake: registra as postagens na "ED-58". */
    static final class RecordingStock implements ChemPorts.StockEntryPort, ChemPorts.StockQueryPort {
        record Entry(UUID tenant, String item, String location, BigDecimal qty, BigDecimal cost, String ref) {}
        final List<Entry> entries = new java.util.ArrayList<>();
        BigDecimal available = BigDecimal.ZERO;
        public void postEntry(UUID t, String item, String loc, BigDecimal qty, BigDecimal cost, String ref) {
            entries.add(new Entry(t, item, loc, qty, cost, ref));
            available = available.add(qty);
        }
        public Optional<StockView> stock(UUID t, String item, String loc) {
            return Optional.of(new StockView(available, available));
        }
    }

    static final class RecordingOrders implements ChemPorts.CleaningOrderPort {
        final List<String> opened = new java.util.ArrayList<>();
        public UUID openCleaningOrder(UUID t, String reservoirCode, LocalDate dueOn, String reason) {
            opened.add(reservoirCode + "@" + dueOn);
            return UUID.randomUUID();
        }
    }
}
