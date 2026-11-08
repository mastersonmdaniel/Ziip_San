package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NormativeCatalogTest {

    @Test void emptyCatalogHasNoStandard() {
        NormativeCatalog empty = NormativeCatalog.empty("V1");
        assertTrue(empty.standardOn("PARAM_A", LocalDate.of(2026, 1, 10)).isEmpty());
        assertTrue(empty.reviewedStandardOn("PARAM_A", LocalDate.of(2026, 1, 10)).isEmpty());
    }

    @Test void unreviewedStandardIsVisibleButNotReviewed() {
        NormativeCatalog c = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", false, false));
        assertTrue(c.standardOn("PARAM_A", LocalDate.of(2026, 1, 10)).isPresent());
        assertTrue(c.reviewedStandardOn("PARAM_A", LocalDate.of(2026, 1, 10)).isEmpty());
    }

    @Test void reviewedStandardRequiresReviewerIdentification() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterStandard(
                "PARAM_A", "A", "u", ParameterStandard.Comparator.MAX, null,
                new java.math.BigDecimal("10"), null, "NORMA", false, true, null,
                LocalDate.of(2026, 1, 1), null));
    }

    @Test void standardWithoutNormReferenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterStandard(
                "PARAM_A", "A", "u", ParameterStandard.Comparator.MAX, null,
                new java.math.BigDecimal("10"), null, " ", false, false, null,
                LocalDate.of(2026, 1, 1), null));
    }

    @Test void comparatorShapeIsEnforced() {
        // MAX sem limite, RANGE invertido e qualitativo sem texto esperado: recusados.
        assertThrows(IllegalArgumentException.class, () -> new ParameterStandard(
                "PARAM_A", "A", "u", ParameterStandard.Comparator.MAX, null, null, null,
                "NORMA", false, false, null, LocalDate.of(2026, 1, 1), null));
        assertThrows(IllegalArgumentException.class, () -> new ParameterStandard(
                "PARAM_A", "A", "u", ParameterStandard.Comparator.RANGE,
                new java.math.BigDecimal("9"), new java.math.BigDecimal("1"), null,
                "NORMA", false, false, null, LocalDate.of(2026, 1, 1), null));
        assertThrows(IllegalArgumentException.class, () -> new ParameterStandard(
                "PARAM_A", "A", null, ParameterStandard.Comparator.QUALITATIVE_EQUALS,
                null, null, null, "NORMA", false, false, null, LocalDate.of(2026, 1, 1), null));
    }

    @Test void duplicateStandardInCatalogIsRefused() {
        ParameterStandard s = TestSupport.maxStandard("PARAM_A", "10", true, false);
        assertThrows(IllegalArgumentException.class,
                () -> new NormativeCatalog("V1", List.of(s, s)));
    }

    @Test void effectivenessWindowIsRespected() {
        ParameterStandard s = TestSupport.maxStandard("PARAM_A", "10", true, false);
        assertTrue(s.effectiveOn(LocalDate.of(2026, 6, 1)));
        assertFalse(s.effectiveOn(LocalDate.of(2025, 12, 31)));
    }
}
