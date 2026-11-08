package br.com.ziipsan.core.apparentloss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.core.apparentloss.LossFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class FraudRulesTest {

    private static List<FraudRules.Rule> rulesOf(List<FraudRules.Suspicion> ss) {
        return ss.stream().map(FraudRules.Suspicion::rule).toList();
    }

    @Test
    void zeroConsumptionActiveFiresAfterConfiguredConsecutiveCycles() {
        UnitFacts u = unitFull(UUID.randomUUID(), null,
                List.of(bd("10"), bd("0"), bd("0"), bd("0")), List.of(bd("50")),
                null, null, null, null, null, false, null);
        assertTrue(rulesOf(FraudRules.evaluate(u, allThresholds(), NOW))
                .contains(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE));
        UnitFacts shortHistory = unitFull(UUID.randomUUID(), null,
                List.of(bd("10"), bd("0"), bd("0")), List.of(bd("50")),
                null, null, null, null, null, false, null);
        assertFalse(rulesOf(FraudRules.evaluate(shortHistory, allThresholds(), NOW))
                .contains(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE));
    }

    @Test
    void abruptDropFiresAgainstBaselineAverage() {
        UnitFacts u = unitFull(UUID.randomUUID(), null,
                List.of(bd("20"), bd("20"), bd("5")), List.of(bd("50")),
                null, null, null, null, null, false, null);
        assertTrue(rulesOf(FraudRules.evaluate(u, allThresholds(), NOW)).contains(FraudRules.Rule.ABRUPT_DROP));
        UnitFacts mild = unitFull(UUID.randomUUID(), null,
                List.of(bd("20"), bd("20"), bd("18")), List.of(bd("50")),
                null, null, null, null, null, false, null);
        assertFalse(rulesOf(FraudRules.evaluate(mild, allThresholds(), NOW)).contains(FraudRules.Rule.ABRUPT_DROP));
    }

    @Test
    void perEconomyAndPerAreaFireAboveTenantCeilings() {
        UnitFacts u = new UnitFacts(UUID.randomUUID(), TENANT, null, UUID.randomUUID(),
                UnitFacts.ConnectionStatus.ACTIVE, PropertyRegistryRecord.PropertyUse.RESIDENTIAL,
                bd("40"), 2, List.of(bd("60")), List.of(bd("50")),
                null, null, null, null, null, false, null);
        List<FraudRules.Rule> rules = rulesOf(FraudRules.evaluate(u, allThresholds(), NOW));
        assertTrue(rules.contains(FraudRules.Rule.PER_ECONOMY), "60/2=30 > 25");
        assertTrue(rules.contains(FraudRules.Rule.PER_AREA), "60/40=1.5 > 0.5");
    }

    @Test
    void stalledMeterNeedsTelemetryFlowWithFrozenReading() {
        UnitFacts withTelemetry = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("100"), bd("100"), bd("100")),
                null, null, null, null, null, true, "12");
        assertTrue(rulesOf(FraudRules.evaluate(withTelemetry, allThresholds(), NOW))
                .contains(FraudRules.Rule.STALLED_METER));
        UnitFacts noTelemetry = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("100"), bd("100"), bd("100")),
                null, null, null, null, null, false, null);
        assertFalse(rulesOf(FraudRules.evaluate(noTelemetry, allThresholds(), NOW))
                .contains(FraudRules.Rule.STALLED_METER),
                "sem telemetria da ED-17, medidor parado nao dispara (mas leitura repetida sim)");
        assertTrue(rulesOf(FraudRules.evaluate(noTelemetry, allThresholds(), NOW))
                .contains(FraudRules.Rule.REPEATED_READING));
    }

    @Test
    void undersizedMeterFiresWhenPeakReachesConfiguredShareOfNominal() {
        UnitFacts u = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("50")),
                "2.5", "2.4", null, null, null, false, null);
        assertTrue(rulesOf(FraudRules.evaluate(u, allThresholds(), NOW))
                .contains(FraudRules.Rule.UNDERSIZED_METER));
        UnitFacts ok = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("50")),
                "2.5", "1.0", null, null, null, false, null);
        assertFalse(rulesOf(FraudRules.evaluate(ok, allThresholds(), NOW))
                .contains(FraudRules.Rule.UNDERSIZED_METER));
    }

    @Test
    void meterReplacementAnomalyFiresOnRecentExchangeWithDrop() {
        UnitFacts u = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("50")),
                null, null, 1, "20", "10", false, null);
        assertTrue(rulesOf(FraudRules.evaluate(u, allThresholds(), NOW))
                .contains(FraudRules.Rule.METER_REPLACEMENT_ANOMALY));
        UnitFacts oldExchange = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("50")),
                null, null, 9, "20", "10", false, null);
        assertFalse(rulesOf(FraudRules.evaluate(oldExchange, allThresholds(), NOW))
                .contains(FraudRules.Rule.METER_REPLACEMENT_ANOMALY));
    }

    @Test
    void repeatedReadingFiresOnIdenticalTailReadings() {
        UnitFacts u = unitFull(UUID.randomUUID(), null,
                List.of(bd("10")), List.of(bd("90"), bd("100"), bd("100"), bd("100")),
                null, null, null, null, null, false, null);
        assertTrue(rulesOf(FraudRules.evaluate(u, allThresholds(), NOW))
                .contains(FraudRules.Rule.REPEATED_READING));
    }

    @Test
    void disabledRulesNeverFire() {
        LossParameters.FraudRuleThresholds none = LossParameters.FraudRuleThresholds.parse("{}");
        UnitFacts worstCase = unitFull(UUID.randomUUID(), null,
                List.of(bd("0"), bd("0"), bd("0"), bd("0")), List.of(bd("100"), bd("100"), bd("100")),
                "2.5", "2.5", 1, "20", "1", true, "30");
        assertTrue(FraudRules.evaluate(worstCase, none, NOW).isEmpty(),
                "tenant sem nenhuma regra habilitada em fraud.rules: nada dispara");
    }

    @Test
    void everySuspicionIsRecommendationOnlyWithEvidence() {
        UnitFacts u = unitFull(UUID.randomUUID(), null,
                List.of(bd("0"), bd("0"), bd("0"), bd("0")), List.of(bd("100"), bd("100"), bd("100")),
                "2.5", "2.5", 1, "20", "1", true, "30");
        List<FraudRules.Suspicion> ss = FraudRules.evaluate(u, allThresholds(), NOW);
        assertFalse(ss.isEmpty());
        for (FraudRules.Suspicion s : ss) {
            assertEquals(FraudRules.Recommendation.HUMAN_INSPECTION_REVIEW, s.recommendation());
            assertEquals(FraudRules.AutomaticEffect.NONE, s.automaticEffect());
            assertFalse(s.evidence().isBlank());
        }
    }

    @Test
    void suspicionRecordRefusesAutomaticEffectAndMissingEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new FraudRules.Suspicion(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), FraudRules.Rule.ABRUPT_DROP,
                " ", NOW, FraudRules.Recommendation.HUMAN_INSPECTION_REVIEW, FraudRules.AutomaticEffect.NONE));
    }
}
