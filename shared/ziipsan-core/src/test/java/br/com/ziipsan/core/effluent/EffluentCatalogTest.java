package br.com.ziipsan.core.effluent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffluentCatalogTest {

    @Test
    void unpublishedNormStandardIsRefused() {
        // A revisao da CONAMA 430 (aprovada, nao publicada): o limite
        // novo NAO entra no catalogo antes da publicacao.
        assertThrows(IllegalArgumentException.class, () -> new EffluentStandard(
                "PARAM_A", "Parametro PARAM_A", "u", EffluentStandard.Comparator.MAX,
                null, new BigDecimal("9"), null, "RESOLUCAO-NOVA [FIXTURE]",
                EffluentStandard.Jurisdiction.FEDERAL, null,
                EffluentStandard.PublicationStatus.UNPUBLISHED, false, null,
                LocalDate.of(2026, 9, 2), null));
    }

    @Test
    void futureVersionIsLoadedButNotYetEffective() {
        EffluentStandard current = TestSupport.stateMax("PARAM_A", "10", true,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        EffluentStandard future = TestSupport.stateMax("PARAM_A", "5", true,
                LocalDate.of(2027, 1, 1), null);
        EffluentCatalog catalog = TestSupport.catalog(current, future);
        LocalDate june = LocalDate.of(2026, 6, 1);
        assertEquals("10", catalog.standardOn("PARAM_A", EffluentStandard.Jurisdiction.STATE, "XX", june)
                .orElseThrow().limitMax().toPlainString());
        assertEquals("5", catalog.standardOn("PARAM_A", EffluentStandard.Jurisdiction.STATE, "XX",
                LocalDate.of(2027, 2, 1)).orElseThrow().limitMax().toPlainString());
        assertEquals(1, catalog.futureStandards(june).size());
    }

    @Test
    void overlappingEffectivenessIsRefusedAtLoad() {
        EffluentStandard a = TestSupport.stateMax("PARAM_A", "10", true,
                LocalDate.of(2026, 1, 1), null);
        EffluentStandard b = TestSupport.stateMax("PARAM_A", "5", true,
                LocalDate.of(2026, 6, 1), null);
        assertThrows(IllegalArgumentException.class, () -> TestSupport.catalog(a, b));
    }

    @Test
    void sameParameterInDifferentJurisdictionsCoexists() {
        EffluentCatalog catalog = TestSupport.catalog(
                TestSupport.federalMax("PARAM_A", "10", true),
                TestSupport.stateMax("PARAM_A", "7", true, LocalDate.of(2026, 1, 1), null));
        assertTrue(catalog.standardOn("PARAM_A", EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1)).isPresent());
        assertTrue(catalog.standardOn("PARAM_A", EffluentStandard.Jurisdiction.STATE, "XX",
                LocalDate.of(2026, 3, 1)).isPresent());
    }

    @Test
    void stateStandardRequiresStateCodeAndFederalRejectsIt() {
        assertThrows(IllegalArgumentException.class, () -> new EffluentStandard(
                "PARAM_A", "P", "u", EffluentStandard.Comparator.MAX, null, new BigDecimal("1"),
                null, "NORMA [FIXTURE]", EffluentStandard.Jurisdiction.STATE, null,
                EffluentStandard.PublicationStatus.PUBLISHED, false, null, LocalDate.of(2026, 1, 1), null));
        assertThrows(IllegalArgumentException.class, () -> new EffluentStandard(
                "PARAM_A", "P", "u", EffluentStandard.Comparator.MAX, null, new BigDecimal("1"),
                null, "NORMA [FIXTURE]", EffluentStandard.Jurisdiction.FEDERAL, "XX",
                EffluentStandard.PublicationStatus.PUBLISHED, false, null, LocalDate.of(2026, 1, 1), null));
    }

    @Test
    void unreviewedStandardDoesNotGroundConformity() {
        EffluentCatalog catalog = TestSupport.catalog(TestSupport.federalMax("PARAM_A", "10", false));
        assertTrue(catalog.standardOn("PARAM_A", EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1)).isPresent());
        assertTrue(catalog.reviewedStandardOn("PARAM_A", EffluentStandard.Jurisdiction.FEDERAL, null,
                LocalDate.of(2026, 3, 1)).isEmpty());
    }

    @Test
    void standardWithoutNormReferenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new EffluentStandard(
                "PARAM_A", "P", "u", EffluentStandard.Comparator.MAX, null, new BigDecimal("1"),
                null, " ", EffluentStandard.Jurisdiction.FEDERAL, null,
                EffluentStandard.PublicationStatus.PUBLISHED, false, null, LocalDate.of(2026, 1, 1), null));
    }
}
