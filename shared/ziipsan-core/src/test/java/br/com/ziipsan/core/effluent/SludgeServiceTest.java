package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SludgeServiceTest {

    private static SludgeService serviceWithCounter(AtomicInteger agendaCalls) {
        return new SludgeService((tenantId, sludgeRecordId, description) -> {
            agendaCalls.incrementAndGet();
            return "AGENDA-ITEM-" + agendaCalls.get();
        });
    }

    @Test
    void dispatchCreatesManualMtrAgendaItemWhileEd104NotReleased() {
        AtomicInteger calls = new AtomicInteger();
        SludgeService service = serviceWithCounter(calls);
        SludgeRecord record = TestSupport.storedSludge("ORIGEM-A");
        MtrLink link = service.dispatch(record, List.of("DEST-A", "DEST-B"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        assertEquals(SludgeRecord.Status.DISPATCHED, record.status());
        assertEquals(1, calls.get());
        assertEquals(MtrLink.Status.PENDING_MANUAL, link.status());
        assertEquals(MtrLink.Source.MANUAL_ED98, link.source());
        assertEquals("AGENDA-ITEM-1", link.agendaItemRef());
        assertNull(link.mtrNumber());
    }

    @Test
    void manualEmissionRecordsMtrNumber() {
        MtrLink link = serviceWithCounter(new AtomicInteger()).dispatch(
                TestSupport.storedSludge("ORIGEM-A"), List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        link.recordManualEmission("MTR-FIXTURE-001", TestSupport.T0.plusSeconds(9000));
        assertEquals(MtrLink.Status.EMITTED_MANUALLY, link.status());
        assertEquals("MTR-FIXTURE-001", link.mtrNumber());
    }

    @Test
    void automaticLinkageWhenEd104Released() {
        MtrLink pending = serviceWithCounter(new AtomicInteger()).dispatch(
                TestSupport.storedSludge("ORIGEM-A"), List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        pending.linkAutomatic("MTR-FIXTURE-002", TestSupport.T0.plusSeconds(10000));
        assertEquals(MtrLink.Status.LINKED_AUTOMATIC, pending.status());
        assertEquals(MtrLink.Source.AUTOMATIC_ED104, pending.source());

        MtrLink manual = serviceWithCounter(new AtomicInteger()).dispatch(
                TestSupport.storedSludge("ORIGEM-A"), List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        manual.recordManualEmission("MTR-FIXTURE-003", TestSupport.T0.plusSeconds(9000));
        manual.linkAutomatic("MTR-FIXTURE-003", TestSupport.T0.plusSeconds(11000));
        assertEquals(MtrLink.Status.LINKED_AUTOMATIC, manual.status());

        MtrLink divergent = serviceWithCounter(new AtomicInteger()).dispatch(
                TestSupport.storedSludge("ORIGEM-A"), List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        divergent.recordManualEmission("MTR-FIXTURE-004", TestSupport.T0.plusSeconds(9000));
        assertThrows(IllegalArgumentException.class,
                () -> divergent.linkAutomatic("MTR-DIVERGENTE", TestSupport.T0.plusSeconds(12000)));
    }

    @Test
    void exemptOriginSkipsAgendaAndMtr() {
        AtomicInteger calls = new AtomicInteger();
        SludgeService service = serviceWithCounter(calls);
        MtrLink link = service.dispatch(TestSupport.storedSludge("ORIGEM-ISENTA"),
                List.of("DEST-A"), List.of("ORIGEM-ISENTA"),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        assertEquals(0, calls.get());
        assertEquals(MtrLink.Status.EXEMPT, link.status());
        assertThrows(IllegalStateException.class,
                () -> link.linkAutomatic("MTR-X", TestSupport.T0.plusSeconds(9000)));
    }

    @Test
    void destinationOutsideTenantCatalogIsRefused() {
        SludgeService service = serviceWithCounter(new AtomicInteger());
        assertThrows(IllegalArgumentException.class, () -> service.dispatch(
                TestSupport.storedSludge("ORIGEM-A"), List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-DESCONHECIDO", "DOC-1", TestSupport.T0.plusSeconds(7200)));
    }

    @Test
    void missingParametersAreExplicitErrors() {
        SludgeService service = serviceWithCounter(new AtomicInteger());
        assertThrows(MissingParameterException.class, () -> service.dispatch(
                TestSupport.storedSludge("ORIGEM-A"), null, List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200)));
        assertThrows(MissingParameterException.class, () -> service.dispatch(
                TestSupport.storedSludge("ORIGEM-A"), List.of("DEST-A"), null,
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200)));
        // Lista vazia de isentas e declaracao valida: nenhuma origem isenta.
        MtrLink link = service.dispatch(TestSupport.storedSludge("ORIGEM-A"),
                List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        assertTrue(link.status() == MtrLink.Status.PENDING_MANUAL);
    }

    @Test
    void aspectSummaryReadsGenerationAndDestinationForEd70() {
        SludgeService service = serviceWithCounter(new AtomicInteger());
        SludgeRecord dispatched = TestSupport.storedSludge("ORIGEM-A");
        service.dispatch(dispatched, List.of("DEST-A"), List.of(),
                "TRANSP-1", "DEST-A", "DOC-1", TestSupport.T0.plusSeconds(7200));
        SludgeRecord pending = TestSupport.generatedSludge("ORIGEM-A");
        var summary = SludgeAspectSummary.summarize(TestSupport.TENANT, List.of(dispatched, pending));
        assertEquals("25.0", summary.generatedByKindAndUnit().get("SLUDGE|m3").toPlainString());
        assertEquals(1, summary.dispatchedCount());
        assertEquals(1, summary.awaitingDestinationCount());
    }
}
