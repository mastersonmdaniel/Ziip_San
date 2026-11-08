package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImplantationPlanTest {

    @Test
    @DisplayName("os dois modelos (60 e 90 dias) são válidos contra a própria régua")
    void templatesAreValid() {
        assertThat(ImplantationPlan.validate(ImplantationPlan.template60())).isEmpty();
        assertThat(ImplantationPlan.validate(ImplantationPlan.template90())).isEmpty();
        assertThat(ImplantationPlan.template60().totalDays()).isEqualTo(60);
        assertThat(ImplantationPlan.template90().totalDays()).isEqualTo(90);
    }

    @Test
    @DisplayName("plano sem um passo obrigatório é acusado, nomeando o passo")
    void missingMandatoryStep() {
        List<ImplantationPlan.Step> steps = new ArrayList<>(ImplantationPlan.template60().steps());
        steps.removeIf(s -> s.key().equals(ImplantationPlan.STEP_TRAINING));
        List<ImplantationPlan.Finding> findings =
                ImplantationPlan.validate(new ImplantationPlan.Plan(60, steps));
        assertThat(findings).anyMatch(f -> f.problem() == ImplantationPlan.Problem.MISSING_MANDATORY_STEP
                && ImplantationPlan.STEP_TRAINING.equals(f.stepKey()));
    }

    @Test
    @DisplayName("passo além do total e passo duplicado são acusados")
    void outOfTotalAndDuplicate() {
        List<ImplantationPlan.Step> steps = new ArrayList<>(ImplantationPlan.template60().steps());
        steps.add(new ImplantationPlan.Step(ImplantationPlan.STEP_TRAINING, "Treinamento extra", 55, 75, false));
        List<ImplantationPlan.Finding> findings =
                ImplantationPlan.validate(new ImplantationPlan.Plan(60, steps));
        assertThat(findings).anyMatch(f -> f.problem() == ImplantationPlan.Problem.STEP_OUT_OF_TOTAL);
        assertThat(findings).anyMatch(f -> f.problem() == ImplantationPlan.Problem.DUPLICATE_STEP);
    }

    @Test
    @DisplayName("ordem trocada dos obrigatórios viola o modelo")
    void orderViolated() {
        List<ImplantationPlan.Step> steps = new ArrayList<>(ImplantationPlan.template60().steps());
        ImplantationPlan.Step first = steps.get(0);
        steps.set(0, steps.get(1));
        steps.set(1, first);
        List<ImplantationPlan.Finding> findings =
                ImplantationPlan.validate(new ImplantationPlan.Plan(60, steps));
        assertThat(findings).anyMatch(f -> f.problem() == ImplantationPlan.Problem.ORDER_VIOLATED);
    }

    @Test
    @DisplayName("marca de gate divergente do modelo é acusada")
    void gateFlagMismatch() {
        List<ImplantationPlan.Step> steps = new ArrayList<>();
        for (ImplantationPlan.Step s : ImplantationPlan.template60().steps()) {
            steps.add(s.key().equals(ImplantationPlan.STEP_PARAMETERS)
                    ? new ImplantationPlan.Step(s.key(), s.title(), s.startDay(), s.endDay(), false)
                    : s);
        }
        List<ImplantationPlan.Finding> findings =
                ImplantationPlan.validate(new ImplantationPlan.Plan(60, steps));
        assertThat(findings).anyMatch(f -> f.problem() == ImplantationPlan.Problem.GATE_FLAG_MISMATCH
                && ImplantationPlan.STEP_PARAMETERS.equals(f.stepKey()));
    }
}
