package br.com.ziipsan.core.apparentloss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.core.apparentloss.LossFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AccuracyMlGateAndSelfReportTest {

    @Test
    void confirmationRatePerRuleIsComputedFromInspections() {
        InspectionAccuracy.Report report = InspectionAccuracy.build(List.of(
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE, 10, 7),
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.ABRUPT_DROP, 4, 1)));
        assertEquals(bd("0.7000"), report.outcome(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE)
                .orElseThrow().confirmationRate().orElseThrow());
        assertEquals(bd("0.2500"), report.outcome(FraudRules.Rule.ABRUPT_DROP)
                .orElseThrow().confirmationRate().orElseThrow());
        assertEquals(bd("0.5714"), report.overallRate().orElseThrow());
        assertEquals(14, report.totalInspected());
        assertEquals(8, report.totalConfirmed());
    }

    @Test
    void ruleWithoutInspectionsHasIndeterminateRateNeverZero() {
        InspectionAccuracy.RuleOutcome o = new InspectionAccuracy.RuleOutcome(FraudRules.Rule.PER_AREA, 0, 0);
        assertTrue(o.confirmationRate().isEmpty());
        assertFalse(o.measured());
        InspectionAccuracy.Report empty = InspectionAccuracy.build(List.of());
        assertTrue(empty.overallRate().isEmpty());
    }

    @Test
    void outcomeRefusesConfirmedAboveInspectedAndDuplicates() {
        assertThrows(IllegalArgumentException.class,
                () -> new InspectionAccuracy.RuleOutcome(FraudRules.Rule.PER_AREA, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> InspectionAccuracy.build(List.of(
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.PER_AREA, 1, 1),
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.PER_AREA, 1, 0))));
    }

    @Test
    void mlGateBlocksUntilEveryEnabledRuleIsMeasuredAndDecisionReferenced() {
        Set<FraudRules.Rule> enabled = Set.of(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE, FraudRules.Rule.ABRUPT_DROP);
        InspectionAccuracy.Report partial = InspectionAccuracy.build(List.of(
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE, 5, 3)));
        MlGate.Assessment blocked = MlGate.assess(enabled, partial, "ATA-ADR0042-TESTE");
        assertTrue(blocked.blocked());
        assertTrue(blocked.gaps().stream().anyMatch(g ->
                g.gap() == MlGate.Gap.RULE_NOT_MEASURED && g.rule() == FraudRules.Rule.ABRUPT_DROP));

        InspectionAccuracy.Report full = InspectionAccuracy.build(List.of(
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE, 5, 3),
                new InspectionAccuracy.RuleOutcome(FraudRules.Rule.ABRUPT_DROP, 2, 1)));
        assertTrue(MlGate.assess(enabled, full, null).blocked(),
                "sem referencia da decisao ADR-0042, bloqueado");
        MlGate.Assessment ready = MlGate.assess(enabled, full, "ATA-ADR0042-TESTE");
        assertEquals(MlGate.Status.READY_FOR_ADR_DECISION, ready.status());
        assertTrue(ready.gaps().isEmpty());
    }

    @Test
    void mlGateBlocksWhenNoRuleEnabled() {
        MlGate.Assessment a = MlGate.assess(Set.of(), InspectionAccuracy.build(List.of()), "ATA");
        assertTrue(a.blocked());
        assertTrue(a.gaps().stream().anyMatch(g -> g.gap() == MlGate.Gap.NO_RULE_ENABLED));
    }

    private ApparentLossService serviceWithSelfReportParam(Optional<String> paramValue) {
        LossPorts.TenantParameters params = (tenantId, name) ->
                LossParameters.SELF_REPORT_FINE_WAIVER_ENABLED.equals(name) ? paramValue : Optional.empty();
        // Correcao D5b: fileSelfReport valida a unidade na porta de fatos —
        // a massa declara uma unidade existente para qualquer id pedido.
        LossPorts.UnitFactsPort anyUnit = new LossPorts.UnitFactsPort() {
            @Override
            public List<UnitFacts> factsForTenant(UUID tenantId) {
                return List.of();
            }

            @Override
            public Optional<UnitFacts> facts(UUID tenantId, UUID unitId) {
                return Optional.of(unit(unitId, null, UnitFacts.ConnectionStatus.ACTIVE,
                        PropertyRegistryRecord.PropertyUse.RESIDENTIAL, "100", 1,
                        List.of(bd("10")), List.of(bd("100"))));
            }
        };
        return new ApparentLossService(new InMemoryLossStore(), null, anyUnit, null, params);
    }

    @Test
    void selfReportOpensWaiverCaseOnlyWhenTenantDeclaredTheProgram() {
        ApparentLossService svc = serviceWithSelfReportParam(Optional.of("true"));
        SelfReport.Case c = svc.fileSelfReport(TENANT, UUID.randomUUID(),
                "ligacao irregular declarada pelo proprio usuario (massa sintetica)", NOW);
        assertTrue(c.fineWaiverApplies());
        assertEquals(SelfReport.CaseStatus.PENDING_HUMAN_REVIEW, c.status());
    }

    @Test
    void selfReportRefusedWhenProgramUndeclaredOrOff() {
        assertThrows(IllegalStateException.class, () -> serviceWithSelfReportParam(Optional.empty())
                .fileSelfReport(TENANT, UUID.randomUUID(), "declaracao", NOW));
        assertThrows(IllegalStateException.class, () -> serviceWithSelfReportParam(Optional.of("false"))
                .fileSelfReport(TENANT, UUID.randomUUID(), "declaracao", NOW));
    }

    @Test
    void selfReportCaseRequiresDeclaredIrregularity() {
        assertThrows(IllegalArgumentException.class, () -> serviceWithSelfReportParam(Optional.of("true"))
                .fileSelfReport(TENANT, UUID.randomUUID(), "  ", NOW));
    }
}
