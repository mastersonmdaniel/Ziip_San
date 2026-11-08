package br.com.ziipsan.core.chemicals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static br.com.ziipsan.core.chemicals.ChemTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MinStockAndExpiryTest {
    static final Instant AT = Instant.parse("2026-03-10T12:00:00Z");

    private InMemoryChemStore storeWithUse(String... actualsByDay) {
        var store = new InMemoryChemStore();
        store.putProduct(product(TENANT, "CLORO", "Cloro granulado"));
        LocalDate base = LocalDate.of(2026, 3, 1);
        for (int i = 0; i < actualsByDay.length; i++)
            store.saveConsumption(new DailyConsumption(TENANT, "CLORO", "ALM-01", "ETA-01",
                    base.plusDays(i), new BigDecimal("2000"), new BigDecimal("0.05"), new BigDecimal(actualsByDay[i])));
        return store;
    }

    @Test void coverageBelowMinimumDaysAlerts() {
        // uso medio 100/dia; disponivel 250 -> 2,50 dias < minimo 7
        var store = storeWithUse("100", "100");
        var stock = new RecordingStock(); stock.available = new BigDecimal("250");
        var params = new TestParams(); params.minStockDays = 7;
        var cov = ChemReports.coverage(store, stock, params, TENANT, "CLORO", LocalDate.of(2026, 3, 10));
        assertEquals(0, new BigDecimal("2.50").compareTo(cov.coverageDays()));
        assertTrue(cov.belowMinimum());
    }

    @Test void coverageAboveMinimumDoesNotAlert() {
        var store = storeWithUse("100", "100");
        var stock = new RecordingStock(); stock.available = new BigDecimal("3000");
        var params = new TestParams(); params.minStockDays = 7;
        var cov = ChemReports.coverage(store, stock, params, TENANT, "CLORO", LocalDate.of(2026, 3, 10));
        assertEquals(0, new BigDecimal("30.00").compareTo(cov.coverageDays()));
        assertFalse(cov.belowMinimum());
    }

    @Test void noConsumptionHistoryIsIndeterminate() {
        var store = new InMemoryChemStore();
        store.putProduct(product(TENANT, "CLORO", "Cloro granulado"));
        var stock = new RecordingStock(); stock.available = new BigDecimal("5");
        var params = new TestParams(); params.minStockDays = 7;
        var cov = ChemReports.coverage(store, stock, params, TENANT, "CLORO", LocalDate.of(2026, 3, 10));
        assertNull(cov.coverageDays());
        assertFalse(cov.belowMinimum());
    }

    @Test void missingMinStockDaysRefusesByName() {
        var store = storeWithUse("100");
        var stock = new RecordingStock();
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class, () ->
                ChemReports.coverage(store, stock, new TestParams(), TENANT, "CLORO", LocalDate.of(2026, 3, 10)));
    }

    @Test void lotExpiryAlertsReuseEd58Rule() {
        var store = new InMemoryChemStore();
        store.saveLotReceipt(new ChemReceipt.LotReceipt(TENANT, "CLORO", "ALM-01", "L-PERTO",
                LocalDate.of(2026, 3, 20), "LAUDO-1", BigDecimal.TEN, AT));
        store.saveLotReceipt(new ChemReceipt.LotReceipt(TENANT, "CLORO", "ALM-01", "L-LONGE",
                LocalDate.of(2027, 3, 20), "LAUDO-2", BigDecimal.TEN, AT));
        var alerts = ChemReports.lotExpiryAlerts(store, TENANT, "CLORO", LocalDate.of(2026, 3, 1), 30);
        assertEquals(1, alerts.size());
        assertEquals("L-PERTO", alerts.get(0).lotCode());
    }
}
