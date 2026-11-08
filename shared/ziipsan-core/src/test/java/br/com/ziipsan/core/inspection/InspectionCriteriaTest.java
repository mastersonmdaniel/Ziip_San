package br.com.ziipsan.core.inspection;

import br.com.ziipsan.core.serviceorder.TargetType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite ED-24: OS de fiscalizacao gerada por criterio (sem duplicar OS aberta). */
class InspectionCriteriaTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 9, 8, 0);

    private static InspectionCriterion.CriterionHit hit() {
        return new InspectionCriterion.CriterionHit(
                InspectionCriterion.EXTERNAL_REPORT, UNIT, "boletim externo 123", AT);
    }

    @Test
    void criterionGeneratesInspectionOrderRequest() {
        InspectionOrderPlanner.Decision decision = InspectionOrderPlanner.plan(hit(), Optional.empty());
        assertEquals(InspectionOrderPlanner.DecisionKind.CREATE_ORDER, decision.kind());
        assertEquals(InspectionOrderTypes.INSPECTION, decision.request().orderTypeCode());
        assertEquals(TargetType.CONSUMER_UNIT, decision.request().targetType());
        assertEquals(UNIT, decision.request().consumerUnitId());
        assertEquals(InspectionCriterion.EXTERNAL_REPORT, decision.request().criterion());
    }

    @Test
    void openOrderForSameUnitAndCriterionAttachesInsteadOfDuplicating() {
        UUID existing = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        InspectionOrderPlanner.Decision decision = InspectionOrderPlanner.plan(hit(),
                Optional.of(new InspectionOrderPlanner.OpenOrderReference(
                        existing, UNIT, InspectionCriterion.EXTERNAL_REPORT)));
        assertEquals(InspectionOrderPlanner.DecisionKind.ATTACH_TO_EXISTING, decision.kind());
        assertEquals(existing, decision.existingOrderId());
        assertNull(decision.request());
    }

    /** D4 da revisao cruzada: dedupe confere unidade+criterio da referencia, nao confia cegamente. */
    @Test
    void openOrderForAnotherUnitIsRefusedByDedupe() {
        UUID existing = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID otherUnit = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
        assertThrows(IllegalArgumentException.class, () -> InspectionOrderPlanner.plan(hit(),
                Optional.of(new InspectionOrderPlanner.OpenOrderReference(
                        existing, otherUnit, InspectionCriterion.EXTERNAL_REPORT))));
    }

    /** D4 da revisao cruzada: idem para criterio divergente na mesma unidade. */
    @Test
    void openOrderForAnotherCriterionIsRefusedByDedupe() {
        UUID existing = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        assertThrows(IllegalArgumentException.class, () -> InspectionOrderPlanner.plan(hit(),
                Optional.of(new InspectionOrderPlanner.OpenOrderReference(
                        existing, UNIT, InspectionCriterion.CONSUMPTION_WHILE_CUT))));
    }

    @Test
    void hitWithoutEvidenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new InspectionCriterion.CriterionHit(
                InspectionCriterion.EXTERNAL_REPORT, UNIT, " ", AT));
    }
}
