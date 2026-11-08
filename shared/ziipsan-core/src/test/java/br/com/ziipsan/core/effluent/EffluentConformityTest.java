package br.com.ziipsan.core.effluent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EffluentConformityTest {

    private static final LocalDate MAR = LocalDate.of(2026, 3, 1);

    @Test
    void conformingAndNonConformingAgainstReviewedFixtureStandard() {
        EffluentCatalog catalog = TestSupport.catalog(TestSupport.federalMax("PARAM_A", "10", true));
        assertEquals(EffluentConformity.Verdict.CONFORMING, EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("8"), null, catalog,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR).verdict());
        assertEquals(EffluentConformity.Verdict.NON_CONFORMING, EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("11"), null, catalog,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR).verdict());
    }

    @Test
    void indeterminateWithoutReviewedStandard() {
        EffluentCatalog unreviewed = TestSupport.catalog(TestSupport.federalMax("PARAM_A", "10", false));
        assertEquals(EffluentConformity.Verdict.INDETERMINATE, EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("8"), null, unreviewed,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR).verdict());
        assertEquals(EffluentConformity.Verdict.INDETERMINATE, EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("8"), null, EffluentCatalog.empty("FIXTURE-0"),
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR).verdict());
    }

    @Test
    void stateStandardAppliesOnlyInItsJurisdiction() {
        EffluentCatalog catalog = TestSupport.catalog(
                TestSupport.stateMax("PARAM_A", "5", true, LocalDate.of(2026, 1, 1), null));
        assertEquals(EffluentConformity.Verdict.NON_CONFORMING, EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("8"), null, catalog,
                EffluentStandard.Jurisdiction.STATE, "XX", MAR).verdict());
        assertEquals(EffluentConformity.Verdict.INDETERMINATE, EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("8"), null, catalog,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR).verdict());
    }

    @Test
    void resultMustHaveExactlyOneForm() {
        EffluentCatalog catalog = TestSupport.catalog(TestSupport.federalMax("PARAM_A", "10", true));
        assertThrows(IllegalArgumentException.class, () -> EffluentConformity.evaluate(
                "PARAM_A", null, null, catalog, EffluentStandard.Jurisdiction.FEDERAL, null, MAR));
        assertThrows(IllegalArgumentException.class, () -> EffluentConformity.evaluate(
                "PARAM_A", new BigDecimal("1"), "TEXTO", catalog,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR));
    }
}
