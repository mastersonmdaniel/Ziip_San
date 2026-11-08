package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyClosingTest {

    private static final YearMonth JAN26 = YearMonth.of(2026, 1);

    private record Fixture(MonthlyClosing closing, NormativeCatalog catalog) {}

    private Fixture fixture() {
        NormativeCatalog catalog = TestSupport.catalog(
                TestSupport.maxStandard("PARAM_A", "10", true, false),
                TestSupport.maxStandard("PARAM_U", "10", false, false));
        SamplingPlan plan = new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 4)));
        Sample s1 = TestSupport.receivedSample("ETQ-201");
        Sample s2 = TestSupport.receivedSample("ETQ-202");
        Sample s3 = TestSupport.receivedSample("ETQ-203");
        AnalysisResult r1 = TestSupport.numericResult(s1, "PARAM_A", "5", true);
        AnalysisResult r2 = TestSupport.numericResult(s2, "PARAM_A", "50", true);
        AnalysisResult r3 = TestSupport.numericResult(s3, "PARAM_U", "1", true);
        LocalDate d = LocalDate.of(2026, 1, 10);
        List<MonthlyClosing.Entry> entries = List.of(
                new MonthlyClosing.Entry(s1, r1, ConformityEvaluator.evaluate(r1, catalog, d)),
                new MonthlyClosing.Entry(s2, r2, ConformityEvaluator.evaluate(r2, catalog, d)),
                new MonthlyClosing.Entry(s3, r3, ConformityEvaluator.evaluate(r3, catalog, d)));
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SIS-1", JAN26,
                plan, List.of(s1, s2, s3), entries);
        return new Fixture(closing, catalog);
    }

    @Test void closingAggregatesByParameter() {
        MonthlyClosing c = fixture().closing();
        MonthlyClosing.ParameterLine a = c.lines().stream()
                .filter(l -> l.parameterKey().equals("PARAM_A")).findFirst().orElseThrow();
        assertEquals(4, a.planned());
        assertEquals(2, a.collected());
        assertEquals(2, a.analyzed());
        assertEquals(1, a.conforming());
        assertEquals(1, a.nonConforming());
        assertEquals(0, a.indeterminate());
        assertEquals(50.0, a.conformityPercent());
        assertEquals(50.0, a.coveragePercent());
    }

    @Test void indeterminateIsCountedSeparatelyNeverAsConforming() {
        MonthlyClosing c = fixture().closing();
        MonthlyClosing.ParameterLine u = c.lines().stream()
                .filter(l -> l.parameterKey().equals("PARAM_U")).findFirst().orElseThrow();
        assertEquals(1, u.indeterminate());
        assertEquals(0, u.conforming());
        assertNull(u.conformityPercent());
    }

    @Test void unvalidatedResultCannotEnterClosing() {
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        SamplingPlan plan = new SamplingPlan(UUID.randomUUID(), "SIS-1",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 1)));
        Sample s = TestSupport.receivedSample("ETQ-204");
        AnalysisResult draft = TestSupport.numericResult(s, "PARAM_A", "5", false);
        ConformityEvaluator.Assessment a = ConformityEvaluator.evaluate(draft, catalog, LocalDate.of(2026, 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new MonthlyClosing.Entry(s, draft, a));
        assertNotNull(plan);
    }

    @Test void closeRequiresTechnicalResponsibleAndCannotRepeat() {
        MonthlyClosing c = fixture().closing();
        assertThrows(IllegalArgumentException.class, () -> c.close(" ", TestSupport.T0));
        c.close("RT-1", TestSupport.T0.plusSeconds(86400));
        assertTrue(c.isClosed());
        assertThrows(IllegalStateException.class, () -> c.close("RT-1", TestSupport.T0.plusSeconds(90000)));
    }

    @Test void billBlockOnlyFromClosedClosing() {
        Fixture f = fixture();
        assertThrows(IllegalStateException.class,
                () -> BillQualityBlockSource.qualityModuleLines(f.closing(), f.catalog()));
        f.closing().close("RT-1", TestSupport.T0.plusSeconds(86400));
        List<BillQualityBlockSource.BillBlockLine> lines =
                BillQualityBlockSource.qualityModuleLines(f.closing(), f.catalog());
        BillQualityBlockSource.BillBlockLine a = lines.stream()
                .filter(l -> l.parameterKey().equals("PARAM_A")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 1, 1), a.referenceMonth());
        assertEquals("SIS-1", a.supplySystem());
        assertTrue(a.resultText().contains("conformes 1 de 2"));
        assertEquals("<= 10", a.referenceText());
        // Parametro sem linha revisada: sem referencia presumida.
        BillQualityBlockSource.BillBlockLine u = lines.stream()
                .filter(l -> l.parameterKey().equals("PARAM_U")).findFirst().orElseThrow();
        assertNull(u.referenceText());
        assertTrue(u.resultText().contains("indeterminada"));
    }

    @Test void planOfAnotherSystemOrMonthIsRefused() {
        SamplingPlan other = new SamplingPlan(UUID.randomUUID(), "SIS-2",
                YearMonth.of(2026, 1), YearMonth.of(2026, 12), "FIXTURE-1",
                List.of(new SamplingPlan.PlanLine(TestSupport.POINT, "PARAM_A", 1)));
        assertThrows(IllegalArgumentException.class, () -> MonthlyClosing.assemble(
                UUID.randomUUID(), "SIS-1", JAN26, other, List.of(), List.of()));
    }
}
