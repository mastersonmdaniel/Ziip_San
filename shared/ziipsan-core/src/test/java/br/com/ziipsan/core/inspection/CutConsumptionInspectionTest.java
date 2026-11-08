package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aceite ED-24 (teste nomeado, v2.15 "20d OS-10"): consumo em ligacao
 * cortada gera OS de fiscalizacao, SEM religacao automatica.
 */
class CutConsumptionInspectionTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 9, 0, 0);

    @Test
    void consumptionWhileCutGeneratesInspectionAndNeverAutoReconnects() {
        CutConsumptionRule.Outcome outcome = CutConsumptionRule.evaluate(new CutConsumptionRule.CutConsumptionFact(
                UNIT, CutConsumptionRule.ConnectionState.CUT, 7, END));
        assertTrue(outcome.generatesInspection());
        assertEquals(InspectionCriterion.CONSUMPTION_WHILE_CUT, outcome.hit().criterion());
        assertFalse(outcome.automaticReconnectionAllowed(), "religacao automatica proibida neste caminho");

        // E o hit abre a OS de fiscalizacao pelo planejador comum.
        InspectionOrderPlanner.Decision decision = InspectionOrderPlanner.plan(outcome.hit(), Optional.empty());
        assertEquals(InspectionOrderPlanner.DecisionKind.CREATE_ORDER, decision.kind());
        assertEquals(UNIT, decision.request().consumerUnitId());
    }

    @Test
    void cutWithoutConsumptionGeneratesNothing() {
        CutConsumptionRule.Outcome outcome = CutConsumptionRule.evaluate(new CutConsumptionRule.CutConsumptionFact(
                UNIT, CutConsumptionRule.ConnectionState.CUT, 0, END));
        assertFalse(outcome.generatesInspection());
        assertFalse(outcome.automaticReconnectionAllowed());
    }

    @Test
    void activeConnectionConsumptionIsNormal() {
        CutConsumptionRule.Outcome outcome = CutConsumptionRule.evaluate(new CutConsumptionRule.CutConsumptionFact(
                UNIT, CutConsumptionRule.ConnectionState.ACTIVE, 12, END));
        assertFalse(outcome.generatesInspection());
    }
}
