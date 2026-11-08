package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategicAssetPlanTest {

    @Test
    @DisplayName("plano sem critério para um dos quatro desfechos é incompleto")
    void fourOutcomesRequired() {
        assertThatThrownBy(() -> new StrategicAssetPlan(Iso55001Fixtures.on(), "Plano",
                List.of("objetivo"),
                List.of(new StrategicAssetPlan.DecisionCriterion("REN-01",
                        StrategicAssetPlan.DecisionOutcome.RENEW, "condição")),
                Iso55001Fixtures.ACCEPTER, LocalDate.of(2026, 1, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DISPOSE");
    }

    @Test
    @DisplayName("decisão exige critério do plano e do mesmo desfecho")
    void decisionBoundToCriterion() {
        StrategicAssetPlan plan = Iso55001AcceptanceOnOffTest.strategicPlan(Iso55001Fixtures.on());
        assertThatThrownBy(() -> plan.decide(Iso55001Fixtures.ASSET,
                StrategicAssetPlan.DecisionOutcome.RENEW, "XXX-99", "motivo",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não existe");
        assertThatThrownBy(() -> plan.decide(Iso55001Fixtures.ASSET,
                StrategicAssetPlan.DecisionOutcome.RENEW, "DES-01", "motivo",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DISPOSE");
    }

    @Test
    @DisplayName("D1: construção DIRETA de DecisionRecord com desfecho diverso do critério é recusada")
    void directDecisionRecordCrossOutcomeRefused() {
        StrategicAssetPlan.DecisionCriterion dispose = new StrategicAssetPlan.DecisionCriterion(
                "DES-01", StrategicAssetPlan.DecisionOutcome.DISPOSE,
                "condição declarada pelo tenant");
        assertThatThrownBy(() -> new StrategicAssetPlan.DecisionRecord(Iso55001Fixtures.ASSET,
                dispose, StrategicAssetPlan.DecisionOutcome.RENEW, "motivo",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DISPOSE");
    }

    @Test
    @DisplayName("D1: construção DIRETA de DecisionRecord coerente com o critério é aceita")
    void directDecisionRecordCoherentAccepted() {
        StrategicAssetPlan.DecisionCriterion renew = new StrategicAssetPlan.DecisionCriterion(
                "REN-01", StrategicAssetPlan.DecisionOutcome.RENEW,
                "condição declarada pelo tenant");
        StrategicAssetPlan.DecisionRecord record = new StrategicAssetPlan.DecisionRecord(
                Iso55001Fixtures.ASSET, renew, StrategicAssetPlan.DecisionOutcome.RENEW,
                "motivo", Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1));
        assertThat(record.outcome()).isEqualTo(StrategicAssetPlan.DecisionOutcome.RENEW);
        assertThat(record.criterion()).isEqualTo(renew);
        assertThat(record.criterionKey()).isEqualTo("REN-01");
    }

    @Test
    @DisplayName("critério sem condição declarada é recusado: nenhum limiar de fábrica")
    void criterionNeedsDeclaredCondition() {
        assertThatThrownBy(() -> new StrategicAssetPlan.DecisionCriterion("REN-01",
                StrategicAssetPlan.DecisionOutcome.RENEW, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
