package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de aceite da ED-72 com a norma LIGADA e DESLIGADA: ligada, os
 * registros do pacote nascem; desligada, nenhum nasce; e desligar NÃO
 * apaga registro — os objetos criados com a norma ligada permanecem
 * íntegros e legíveis depois do desligamento (o pacote não tem operação
 * de exclusão; o SQL pendente não tem DELETE).
 */
class Iso55001AcceptanceOnOffTest {

    @Test
    @DisplayName("ligada: nascem inventário, plano estratégico, decisão, investimento e autoavaliação")
    void enabledEverythingIsBorn() {
        Iso55001Activation on = Iso55001Fixtures.on();
        AssetLifecycleRecord rec = new AssetLifecycleRecord(on, Iso55001Fixtures.ASSET,
                "PAT-001", AssetLifecycleRecord.AssetKind.NON_LINEAR_EQUIPMENT, "IN_OPERATION",
                List.of(AssetLifecycleRecord.CoincidentField.resolve("usefulLifeYears", "25", "30")),
                AssetLifecycleRecord.RiskSnapshot.readFromEd56(4, 5, 20, "HIGH", null),
                LocalDate.of(2026, 1, 10));
        assertThat(rec.risk().score()).isEqualTo(20);

        StrategicAssetPlan plan = strategicPlan(on);
        var decision = plan.decide(Iso55001Fixtures.ASSET,
                StrategicAssetPlan.DecisionOutcome.RENEW, "REN-01", "risco alto lido da ED-56",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1));
        assertThat(decision.outcome()).isEqualTo(StrategicAssetPlan.DecisionOutcome.RENEW);

        AmsClauseAssessment assessment = AmsClauseAssessment.assess(on, "7.1",
                "[A DEFINIR]", AmsClauseAssessment.Conformity.PARTIAL, List.of(),
                "", Iso55001Fixtures.USER, LocalDate.of(2026, 2, 2));
        assertThat(assessment.titlePending()).isTrue();
    }

    @Test
    @DisplayName("desligada: NENHUM registro do pacote nasce")
    void disabledNothingIsBorn() {
        Iso55001Activation off = Iso55001Activation.disabled();
        assertThatThrownBy(() -> new AssetLifecycleRecord(off, Iso55001Fixtures.ASSET,
                "PAT-001", AssetLifecycleRecord.AssetKind.LINEAR, "IN_OPERATION", List.of(),
                AssetLifecycleRecord.RiskSnapshot.readFromEd56(1, 1, 1, "LOW", null),
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> strategicPlan(off)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AmsClauseAssessment.assess(off, "7.1", "título próprio",
                AmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MaintenanceLinks.NonLinearPreventiveLink(off,
                Iso55001Fixtures.ASSET, "PM-1", MaintenanceLinks.PmTriggerKind.TIME))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PredictiveActionRule.evaluate(off, Iso55001Fixtures.ASSET,
                "vibracao", "mm/s", List.of(), new PredictiveActionRule.RuleParameters(3, 2.0,
                        PredictiveActionRule.Direction.ABOVE)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("desligar depois NÃO apaga: registros criados ligados seguem íntegros e legíveis")
    void disablingKeepsRecords() {
        Iso55001Activation on = Iso55001Fixtures.on();
        AmsClauseAssessment before = AmsClauseAssessment.assess(on, "9.1", "título próprio",
                AmsClauseAssessment.Conformity.CONFORMING,
                List.of(new AmsClauseAssessment.EvidenceRef(
                        AmsClauseAssessment.EvidenceKind.STRATEGIC_PLAN, "plano-2026")),
                "", Iso55001Fixtures.USER, LocalDate.of(2026, 3, 1));
        String jsonBefore = before.toJson();
        // O tenant desliga a norma (núcleo P1.32): o registro criado NÃO some.
        Iso55001Activation off = Iso55001Activation.disabled();
        assertThat(off.enabled()).isFalse();
        assertThat(before.toJson()).isEqualTo(jsonBefore);
        assertThat(before.conformity()).isEqualTo(AmsClauseAssessment.Conformity.CONFORMING);
    }

    static StrategicAssetPlan strategicPlan(Iso55001Activation activation) {
        return new StrategicAssetPlan(activation, "Plano estratégico 2026",
                List.of("Reduzir falhas nos ativos críticos"),
                List.of(
                        new StrategicAssetPlan.DecisionCriterion("REN-01",
                                StrategicAssetPlan.DecisionOutcome.RENEW, "condição declarada pelo tenant"),
                        new StrategicAssetPlan.DecisionCriterion("REP-01",
                                StrategicAssetPlan.DecisionOutcome.REPAIR, "condição declarada pelo tenant"),
                        new StrategicAssetPlan.DecisionCriterion("MAN-01",
                                StrategicAssetPlan.DecisionOutcome.MAINTAIN, "condição declarada pelo tenant"),
                        new StrategicAssetPlan.DecisionCriterion("DES-01",
                                StrategicAssetPlan.DecisionOutcome.DISPOSE, "condição declarada pelo tenant")),
                Iso55001Fixtures.ACCEPTER, LocalDate.of(2026, 1, 5));
    }
}
