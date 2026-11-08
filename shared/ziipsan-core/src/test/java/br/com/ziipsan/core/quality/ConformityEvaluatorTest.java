package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ConformityEvaluatorTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 10);

    @Test void maxConformingAndNonConforming() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        Sample s1 = TestSupport.receivedSample("ETQ-101");
        assertEquals(ConformityEvaluator.Verdict.CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.numericResult(s1, "PARAM_A", "10", true), c, D).verdict());
        Sample s2 = TestSupport.receivedSample("ETQ-102");
        assertEquals(ConformityEvaluator.Verdict.NON_CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.numericResult(s2, "PARAM_A", "10.01", true), c, D).verdict());
    }

    @Test void rangeBoundariesAreInclusive() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.rangeStandard("PARAM_B", true));
        Sample s1 = TestSupport.receivedSample("ETQ-103");
        assertEquals(ConformityEvaluator.Verdict.CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.numericResult(s1, "PARAM_B", "2", true), c, D).verdict());
        Sample s2 = TestSupport.receivedSample("ETQ-104");
        assertEquals(ConformityEvaluator.Verdict.CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.numericResult(s2, "PARAM_B", "8", true), c, D).verdict());
        Sample s3 = TestSupport.receivedSample("ETQ-105");
        assertEquals(ConformityEvaluator.Verdict.NON_CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.numericResult(s3, "PARAM_B", "1.99", true), c, D).verdict());
    }

    @Test void qualitativeEquality() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.qualitativeStandard("PARAM_Q", true));
        Sample s1 = TestSupport.receivedSample("ETQ-106");
        assertEquals(ConformityEvaluator.Verdict.CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.qualitativeResult(s1, "PARAM_Q", "AUSENTE", true), c, D).verdict());
        Sample s2 = TestSupport.receivedSample("ETQ-107");
        assertEquals(ConformityEvaluator.Verdict.NON_CONFORMING,
                ConformityEvaluator.evaluate(TestSupport.qualitativeResult(s2, "PARAM_Q", "PRESENTE", true), c, D).verdict());
    }

    @Test void noStandardMeansIndeterminateNeverPresumed() {
        NormativeCatalog empty = NormativeCatalog.empty("V0");
        Sample s = TestSupport.receivedSample("ETQ-108");
        ConformityEvaluator.Assessment a = ConformityEvaluator.evaluate(
                TestSupport.numericResult(s, "PARAM_A", "1", true), empty, D);
        assertEquals(ConformityEvaluator.Verdict.INDETERMINATE, a.verdict());
        assertFalse(a.healthRiskRelevant());
    }

    @Test void unreviewedStandardMeansIndeterminate() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", false, true));
        Sample s = TestSupport.receivedSample("ETQ-109");
        ConformityEvaluator.Assessment a = ConformityEvaluator.evaluate(
                TestSupport.numericResult(s, "PARAM_A", "999", true), c, D);
        assertEquals(ConformityEvaluator.Verdict.INDETERMINATE, a.verdict());
        // A marcacao de risco da linha NAO revisada nao dispara comunicacao.
        assertFalse(a.healthRiskRelevant());
    }

    @Test void healthRiskFlagComesFromReviewedCatalogLine() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, true));
        Sample s = TestSupport.receivedSample("ETQ-110");
        ConformityEvaluator.Assessment a = ConformityEvaluator.evaluate(
                TestSupport.numericResult(s, "PARAM_A", "11", true), c, D);
        assertEquals(ConformityEvaluator.Verdict.NON_CONFORMING, a.verdict());
        assertTrue(a.healthRiskRelevant());
    }

    @Test void formMismatchIsRefusedNotPresumed() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        Sample s = TestSupport.receivedSample("ETQ-111");
        AnalysisResult qualitative = TestSupport.qualitativeResult(s, "PARAM_A", "TEXTO", true);
        assertThrows(IllegalArgumentException.class,
                () -> ConformityEvaluator.evaluate(qualitative, c, D));
    }
}
