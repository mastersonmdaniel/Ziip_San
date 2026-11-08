package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static br.com.ziipsan.core.chemicals.ChemTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ReservoirCleaningTest {

    private InMemoryChemStore storeWithReservoir() {
        var store = new InMemoryChemStore();
        store.putReservoir(new ReservoirCleaning.Reservoir(TENANT, "RES-01", "Reservatorio Central", "SISTEMA-A", true));
        return store;
    }

    @Test void planRequiresAgencyInterval() {
        var store = storeWithReservoir();
        assertThrows(MissingParameterException.class, () ->
                ReservoirCleaning.createPlan(new TestParams(), TENANT, "RES-01", LocalDate.of(2026, 1, 10), null));
    }

    @Test void dueDateIsLastCleaningPlusInterval() {
        var params = new TestParams(); params.intervalMonths = 6;
        var plan = ReservoirCleaning.createPlan(params, TENANT, "RES-01",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10));
        assertEquals(LocalDate.of(2026, 7, 10), plan.nextDueOn());
        assertFalse(plan.dueOn(LocalDate.of(2026, 7, 9)));
        assertTrue(plan.dueOn(LocalDate.of(2026, 7, 10)));
        assertTrue(plan.overdueOn(LocalDate.of(2026, 7, 11)));
    }

    @Test void dueOrderGenerationIsIdempotent() {
        var store = storeWithReservoir();
        var params = new TestParams(); params.intervalMonths = 6;
        store.saveCleaningPlan(ReservoirCleaning.createPlan(params, TENANT, "RES-01",
                LocalDate.of(2025, 7, 10), LocalDate.of(2025, 7, 10)));
        var orders = new RecordingOrders();
        var first = ReservoirCleaning.generateDueOrders(store, orders, TENANT, LocalDate.of(2026, 1, 10));
        var second = ReservoirCleaning.generateDueOrders(store, orders, TENANT, LocalDate.of(2026, 1, 11));
        assertEquals(1, first.size());
        assertEquals(0, second.size());
        assertEquals(1, orders.opened.size());
    }

    @Test void executionRequiresReportAndAdvancesPlan() {
        var store = storeWithReservoir();
        var params = new TestParams(); params.intervalMonths = 6;
        store.saveCleaningPlan(ReservoirCleaning.createPlan(params, TENANT, "RES-01",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)));
        UUID order = UUID.randomUUID();
        assertThrows(ChemExceptions.ValidationException.class, () ->
                new ReservoirCleaning.CleaningExecution(TENANT, "RES-01", order, LocalDate.of(2026, 7, 8),
                        " ", "CLORO", BigDecimal.ONE, new BigDecimal("120")));
        // R4: produto cadastrado + OS gerada para RES-01 no vencimento
        // 2026-07-10 com ESTE id — a execucao valida casa os dois.
        store.putProduct(product(TENANT, "CLORO", "Cloro granulado"));
        store.markCleaningOrderGenerated(TENANT, "RES-01", LocalDate.of(2026, 7, 10), order);
        var advanced = ReservoirCleaning.recordExecution(store,
                new ReservoirCleaning.CleaningExecution(TENANT, "RES-01", order, LocalDate.of(2026, 7, 8),
                        "LAUDO-LIMP-01", "CLORO", new BigDecimal("2.5"), new BigDecimal("120")));
        assertEquals(LocalDate.of(2027, 1, 8), advanced.nextDueOn());
        assertEquals(LocalDate.of(2026, 7, 8), advanced.lastCleaningOn());
    }

    @Test void executionWithUngeneratedOrderRefused() {
        // R4: UUID qualquer (OS nao gerada para reservatorio/vencimento)
        var store = storeWithReservoir();
        var params = new TestParams(); params.intervalMonths = 6;
        store.saveCleaningPlan(ReservoirCleaning.createPlan(params, TENANT, "RES-01",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)));
        store.putProduct(product(TENANT, "CLORO", "Cloro granulado"));
        var e = assertThrows(ChemExceptions.ValidationException.class, () ->
                ReservoirCleaning.recordExecution(store,
                        new ReservoirCleaning.CleaningExecution(TENANT, "RES-01", UUID.randomUUID(),
                                LocalDate.of(2026, 7, 8), "LAUDO-LIMP-01", "CLORO",
                                new BigDecimal("2.5"), new BigDecimal("120"))));
        assertEquals("CLEANING_ORDER_NOT_GENERATED", e.code());
        assertTrue(store.cleaningExecutions(TENANT).isEmpty());
        assertEquals(LocalDate.of(2026, 7, 10),
                store.cleaningPlan(TENANT, "RES-01").orElseThrow().nextDueOn());
        // OS gerada mas com OUTRO id tambem recusa (mismatch nomeado)
        store.markCleaningOrderGenerated(TENANT, "RES-01", LocalDate.of(2026, 7, 10), UUID.randomUUID());
        var e2 = assertThrows(ChemExceptions.ValidationException.class, () ->
                ReservoirCleaning.recordExecution(store,
                        new ReservoirCleaning.CleaningExecution(TENANT, "RES-01", UUID.randomUUID(),
                                LocalDate.of(2026, 7, 8), "LAUDO-LIMP-01", "CLORO",
                                new BigDecimal("2.5"), new BigDecimal("120"))));
        assertEquals("CLEANING_ORDER_MISMATCH", e2.code());
        assertTrue(store.cleaningExecutions(TENANT).isEmpty());
    }

    @Test void executionWithUnregisteredProductRefused() {
        // R4: produto nao cadastrado no tenant nao encerra limpeza
        var store = storeWithReservoir();
        var params = new TestParams(); params.intervalMonths = 6;
        store.saveCleaningPlan(ReservoirCleaning.createPlan(params, TENANT, "RES-01",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)));
        UUID order = UUID.randomUUID();
        store.markCleaningOrderGenerated(TENANT, "RES-01", LocalDate.of(2026, 7, 10), order);
        var e = assertThrows(ChemExceptions.NotFoundException.class, () ->
                ReservoirCleaning.recordExecution(store,
                        new ReservoirCleaning.CleaningExecution(TENANT, "RES-01", order,
                                LocalDate.of(2026, 7, 8), "LAUDO-LIMP-01", "FANTASMA",
                                new BigDecimal("2.5"), new BigDecimal("120"))));
        assertEquals("CHEM_PRODUCT_NOT_FOUND", e.code());
        assertTrue(store.cleaningExecutions(TENANT).isEmpty());
        assertEquals(LocalDate.of(2026, 7, 10),
                store.cleaningPlan(TENANT, "RES-01").orElseThrow().nextDueOn());
    }

    @Test void publicAnnualAndDiscardedSources() {
        var store = storeWithReservoir();
        store.putReservoir(new ReservoirCleaning.Reservoir(TENANT, "RES-02", "Reservatorio Alto", "SISTEMA-A", true));
        UUID o1 = UUID.randomUUID(), o2 = UUID.randomUUID();
        store.saveCleaningExecution(new ReservoirCleaning.CleaningExecution(TENANT, "RES-01", o1,
                LocalDate.of(2026, 3, 15), "LAUDO-1", "CLORO", BigDecimal.ONE, new BigDecimal("100.00")));
        store.saveCleaningExecution(new ReservoirCleaning.CleaningExecution(TENANT, "RES-02", o2,
                LocalDate.of(2025, 12, 20), "LAUDO-2", "CLORO", BigDecimal.ONE, new BigDecimal("50.00")));
        var pub = ChemReports.publicCleaningRecords(store, TENANT);
        assertEquals(2, pub.size());
        assertEquals("Reservatorio Central", pub.get(1).reservoirName());
        var annual2026 = ChemReports.annualReportSource(store, TENANT, 2026);
        assertEquals(1, annual2026.size());
        assertEquals("RES-01", annual2026.get(0).reservoirCode());
        var discarded = ChemReports.discardedVolumes(store, TENANT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(1, discarded.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(discarded.get(0).totalDiscardedM3()));
        // periodo sem execucao: lista vazia, nao zero presumido
        assertTrue(ChemReports.discardedVolumes(store, TENANT, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).isEmpty());
    }

    @Test void overduePlansListed() {
        var store = storeWithReservoir();
        var params = new TestParams(); params.intervalMonths = 6;
        store.saveCleaningPlan(ReservoirCleaning.createPlan(params, TENANT, "RES-01",
                LocalDate.of(2025, 7, 10), LocalDate.of(2025, 7, 10)));
        assertEquals(1, ChemReports.overdueCleaningPlans(store, TENANT, LocalDate.of(2026, 1, 11)).size());
        assertEquals(0, ChemReports.overdueCleaningPlans(store, TENANT, LocalDate.of(2026, 1, 10)).size());
    }
}
