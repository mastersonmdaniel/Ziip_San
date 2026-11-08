package br.com.ziipsan.core.quality;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SisaguaAndPortalTest {

    private record Fx(MonthlyClosing closing, List<MonthlyClosing.Entry> entries, Sample complaint) {}

    private Fx fixture() {
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        SamplingPlan plan = new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 2)));
        Sample routine = TestSupport.receivedSample("ETQ-401");
        routine.markAnalyzed();
        routine.markValidated();
        AnalysisResult rr = TestSupport.numericResult(routine, "PARAM_A", "5", true);
        LocalDate d = LocalDate.of(2026, 1, 10);
        MonthlyClosing.Entry e1 = new MonthlyClosing.Entry(routine, rr,
                ConformityEvaluator.evaluate(rr, catalog, d));

        Sample complaint = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.CONSUMER_POINT,
                Sample.SampleType.COMPLAINT, "ETQ-402", UUID.randomUUID(), UUID.randomUUID());
        complaint.collect("C-1", TestSupport.T0);
        complaint.sealAndDispatch("C-1", TestSupport.T0.plusSeconds(60));
        complaint.receiveByLab("LAB-1", TestSupport.T0.plusSeconds(3600));
        complaint.markAnalyzed();
        complaint.markValidated();
        AnalysisResult cr = TestSupport.numericResult(complaint, "PARAM_A", "7", true);
        MonthlyClosing.Entry e2 = new MonthlyClosing.Entry(complaint, cr,
                ConformityEvaluator.evaluate(cr, catalog, d));

        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), plan, List.of(routine, complaint), List.of(e1, e2));
        closing.close("RT-1", TestSupport.T0.plusSeconds(86400));
        return new Fx(closing, List.of(e1, e2), complaint);
    }

    @Test void sisaguaRefusesWithoutConfiguredLayout() {
        Fx fx = fixture();
        assertThrows(MissingParameterException.class,
                () -> SisaguaExport.generate(fx.closing(), fx.entries(), null));
        assertThrows(MissingParameterException.class,
                () -> SisaguaExport.generate(fx.closing(), fx.entries(), " "));
    }

    @Test void sisaguaContentMatchesClosingDataDeterministically() {
        Fx fx = fixture();
        SisaguaExport.SisaguaFile a = SisaguaExport.generate(fx.closing(), fx.entries(), "LAYOUT-FIXTURE");
        SisaguaExport.SisaguaFile b = SisaguaExport.generate(
                fx.closing(), List.of(fx.entries().get(1), fx.entries().get(0)), "LAYOUT-FIXTURE");
        assertEquals(a.content(), b.content());
        assertEquals(2, a.rowCount());
        assertTrue(a.content().contains("ETQ-401"));
        assertTrue(a.content().contains("PARAM_A"));
        assertTrue(a.content().contains("CONFORMING"));
        assertTrue(a.fileName().contains("2026-01"));
    }

    @Test void sisaguaRequiresClosedClosing() {
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        SamplingPlan plan = new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 1)));
        MonthlyClosing open = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), plan, List.of(), List.of());
        assertThrows(IllegalStateException.class,
                () -> SisaguaExport.generate(open, List.of(), "LAYOUT-FIXTURE"));
        assertNotNull(catalog);
    }

    @Test void portalPublishesRoutineButNeverComplaint() {
        Fx fx = fixture();
        List<PortalQualityPublication.PublishedResult> all =
                PortalQualityPublication.forAllPoints(fx.entries());
        assertEquals(1, all.size());
        assertEquals(TestSupport.POINT, all.get(0).pointId());
        assertEquals("5", all.get(0).resultText());
        assertTrue(PortalQualityPublication.forPoint(TestSupport.CONSUMER_POINT, fx.entries()).isEmpty());
        List<PortalQualityPublication.PublishedResult> byPoint =
                PortalQualityPublication.forPoint(TestSupport.POINT, fx.entries());
        assertEquals(1, byPoint.size());
    }
}
