package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite ED-24: fonte alternativa declarada no campo da P1.3 (CAD-69) entra nos criterios. */
class AlternativeSourceCriterionTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 9, 9, 0);

    private static AlternativeSourceCriterion.DeclaredAlternativeSource declared(boolean authorized) {
        return new AlternativeSourceCriterion.DeclaredAlternativeSource(
                UNIT, AlternativeSourceCriterion.AlternativeSourceType.DEEP_WELL,
                authorized, true, 40L, AT);
    }

    @Test
    void declaredSourceEntersInspectionCriteria() {
        AlternativeSourceCriterion.Assessment assessment =
                AlternativeSourceCriterion.assess(declared(true), true);
        assertTrue(assessment.entersInspectionCriteria());
        assertFalse(assessment.generatesInspectionOrder(), "fonte autorizada entra no conjunto, sem OS automatica");
    }

    @Test
    void unauthorizedSourceGeneratesInspectionOrder() {
        AlternativeSourceCriterion.Assessment assessment =
                AlternativeSourceCriterion.assess(declared(false), true);
        assertTrue(assessment.entersInspectionCriteria());
        assertTrue(assessment.generatesInspectionOrder());
        assertEquals(InspectionCriterion.ALTERNATIVE_SOURCE_UNAUTHORIZED, assessment.unauthorizedHit().criterion());

        InspectionOrderPlanner.Decision decision =
                InspectionOrderPlanner.plan(assessment.unauthorizedHit(), Optional.empty());
        assertEquals(InspectionOrderPlanner.DecisionKind.CREATE_ORDER, decision.kind());
    }

    @Test
    void tenantNotRequiringAuthorizationDoesNotGenerateOrder() {
        AlternativeSourceCriterion.Assessment assessment =
                AlternativeSourceCriterion.assess(declared(false), false);
        assertTrue(assessment.entersInspectionCriteria());
        assertFalse(assessment.generatesInspectionOrder());
    }

    @Test
    void noDeclaredSourceNoCriterion() {
        AlternativeSourceCriterion.Assessment assessment = AlternativeSourceCriterion.assess(null, true);
        assertFalse(assessment.entersInspectionCriteria());
        assertFalse(assessment.generatesInspectionOrder());
        assertNull(assessment.declaredHit());
    }

    /** D3 da revisao cruzada: fonte declarada produz CriterionHit auditavel, nao so booleano. */
    @Test
    void declaredSourceProducesAuditableDeclaredHit() {
        AlternativeSourceCriterion.Assessment assessment =
                AlternativeSourceCriterion.assess(declared(true), true);
        assertNotNull(assessment.declaredHit(), "fonte declarada deve materializar hit auditavel (D3)");
        assertEquals(InspectionCriterion.ALTERNATIVE_SOURCE_DECLARED, assessment.declaredHit().criterion());
        assertEquals(UNIT, assessment.declaredHit().consumerUnitId());
        assertEquals(AT, assessment.declaredHit().detectedAt());
        assertFalse(assessment.declaredHit().evidenceSummary().isBlank());
        // O hit declarado, sozinho, nao abre OS: quem abre e o hit de nao autorizada.
        assertFalse(assessment.generatesInspectionOrder());

        AlternativeSourceCriterion.Assessment unauthorized =
                AlternativeSourceCriterion.assess(declared(false), true);
        assertNotNull(unauthorized.declaredHit());
        assertEquals(InspectionCriterion.ALTERNATIVE_SOURCE_DECLARED, unauthorized.declaredHit().criterion());
        assertNotNull(unauthorized.unauthorizedHit());
    }
}
