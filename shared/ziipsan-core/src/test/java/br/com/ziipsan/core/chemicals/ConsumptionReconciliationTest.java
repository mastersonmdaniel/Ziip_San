package br.com.ziipsan.core.chemicals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static br.com.ziipsan.core.chemicals.ChemTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsumptionReconciliationTest {
    static final Instant AT = Instant.parse("2026-03-02T12:00:00Z");

    private DailyConsumption day(String dosage, String volume, String actual) {
        return new DailyConsumption(TENANT, "CLORO", "ALM-01", "ETA-01", LocalDate.of(2026, 3, 1),
                new BigDecimal(volume), new BigDecimal(dosage), new BigDecimal(actual));
    }

    @Test void theoreticalIsDeclaredDosageTimesTreatedVolume() {
        // dosagem declarada 0,05 (unidade do item/m3) x 2.000 m3 = 100,0000
        var r = day("0.05", "2000", "100").reconcile(null);
        assertEquals(0, new BigDecimal("100.0000").compareTo(r.theoreticalQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.varianceQty()));
    }

    @Test void withoutToleranceStatusIsReportedNeverPresumed() {
        var r = day("0.05", "2000", "150").reconcile(null);
        assertEquals(DailyConsumption.Status.REPORTED, r.status());
        assertEquals(0, new BigDecimal("50.0000").compareTo(r.varianceQty()));
        assertEquals(0, new BigDecimal("50.0000").compareTo(r.variancePct()));
    }

    @Test void withToleranceVerdictIsComputed() {
        // variacao de 5% dentro de 10% -> concilia; 50% -> diverge
        assertEquals(DailyConsumption.Status.CONCILED, day("0.05", "2000", "105").reconcile(new BigDecimal("10")).status());
        assertEquals(DailyConsumption.Status.DIVERGENT, day("0.05", "2000", "150").reconcile(new BigDecimal("10")).status());
    }

    @Test void perM3AndZeroVolumeRules() {
        assertEquals(0, new BigDecimal("0.050000").compareTo(day("0.05", "2000", "100").actualPerM3()));
        assertThrows(IllegalArgumentException.class, () -> day("0.05", "0", "0"));
        var zero = new DailyConsumption(TENANT, "CLORO", "ALM-01", "ETA-01", LocalDate.of(2026, 3, 1),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertNull(zero.actualPerM3());
        assertEquals(DailyConsumption.Status.CONCILED, zero.reconcile(new BigDecimal("10")).status());
    }

    @Test void serviceRecordsAndAlertsOnDivergence() {
        var store = new InMemoryChemStore(); var stock = new RecordingStock();
        var params = new TestParams(); params.tolerancePct = new BigDecimal("10");
        var svc = new ChemService(store, params, stock);
        svc.registerProduct(product(TENANT, "CLORO", "Cloro granulado"), chemItem("CLORO", "Cloro granulado"), ACTOR, AT);
        var r = svc.recordConsumption(day("0.05", "2000", "150"), ACTOR, AT);
        assertEquals(DailyConsumption.Status.DIVERGENT, r.status());
        assertEquals(1, store.alerts(TENANT).size());
        assertEquals(ChemPorts.AlertKind.RECONCILIATION_DIVERGENT, store.alerts(TENANT).get(0).kind());
        assertEquals(1, store.consumptions(TENANT, "CLORO").size());
    }

    @Test void dailyReportAggregatesRows() {
        var store = new InMemoryChemStore();
        store.saveConsumption(day("0.05", "2000", "100"));
        var rows = ChemReports.dailyConsumption(store, TENANT, "CLORO",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null);
        assertEquals(1, rows.size());
        assertEquals(DailyConsumption.Status.REPORTED, rows.get(0).status());
        assertEquals(0, new BigDecimal("2000").compareTo(rows.get(0).treatedVolumeM3()));
    }
}
