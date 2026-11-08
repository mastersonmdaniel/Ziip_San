package br.com.ziipsan.platform.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingPlanTest {

    @Test
    @DisplayName("todo módulo do catálogo tem plano-padrão válido de exatamente 40 h")
    void standardPlansAreValid40() {
        assertThat(TrainingPlan.CATALOG).isNotEmpty();
        for (TrainingPlan.TrainingModule m : TrainingPlan.CATALOG) {
            TrainingPlan.ModulePlan plan = TrainingPlan.standardPlan(m.key());
            assertThat(plan.totalHours()).as(m.key()).isEqualTo(40);
            assertThat(TrainingPlan.validate(plan)).as(m.key()).isEmpty();
            assertThat(m.sourceRefs()).as(m.key()).isNotEmpty();
            assertThat(m.audiences()).as(m.key()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("plano com 39 h é acusado: o aceite exige 40 h por módulo")
    void thirtyNineHoursFails() {
        List<TrainingPlan.Unit> units = new ArrayList<>(
                TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO).units());
        TrainingPlan.Unit last = units.remove(units.size() - 1);
        units.add(new TrainingPlan.Unit(last.key(), last.title(), last.hours() - 1));
        List<TrainingPlan.Finding> findings = TrainingPlan.validate(
                new TrainingPlan.ModulePlan(TrainingPlan.MOD_CADASTRO, units));
        assertThat(findings).anyMatch(f -> f.problem() == TrainingPlan.Problem.TOTAL_DIFFERS_FROM_40);
    }

    @Test
    @DisplayName("redistribuir as horas entre unidades mantendo 40 h continua válido")
    void redistributionKeeping40IsValid() {
        TrainingPlan.ModulePlan plan = new TrainingPlan.ModulePlan(TrainingPlan.MOD_FISCAL, List.of(
                new TrainingPlan.Unit(TrainingPlan.UNIT_FUNDAMENTOS, "Fundamentos", 10),
                new TrainingPlan.Unit(TrainingPlan.UNIT_OPERACAO_GUIADA, "Guiada", 10),
                new TrainingPlan.Unit(TrainingPlan.UNIT_PRATICA, "Prática", 12),
                new TrainingPlan.Unit(TrainingPlan.UNIT_EXCECOES, "Exceções", 4),
                new TrainingPlan.Unit(TrainingPlan.UNIT_AVALIACAO, "Avaliação", 4)));
        assertThat(TrainingPlan.validate(plan)).isEmpty();
    }

    @Test
    @DisplayName("unidade duplicada e módulo fora do catálogo são acusados")
    void duplicateAndUnknown() {
        TrainingPlan.ModulePlan dup = new TrainingPlan.ModulePlan(TrainingPlan.MOD_GIS, List.of(
                new TrainingPlan.Unit(TrainingPlan.UNIT_FUNDAMENTOS, "A", 20),
                new TrainingPlan.Unit(TrainingPlan.UNIT_FUNDAMENTOS, "B", 20)));
        assertThat(TrainingPlan.validate(dup))
                .anyMatch(f -> f.problem() == TrainingPlan.Problem.DUPLICATE_UNIT);

        TrainingPlan.ModulePlan unknown = new TrainingPlan.ModulePlan("MODULO_INVENTADO", List.of(
                new TrainingPlan.Unit(TrainingPlan.UNIT_FUNDAMENTOS, "A", 40)));
        assertThat(TrainingPlan.validate(unknown))
                .anyMatch(f -> f.problem() == TrainingPlan.Problem.UNKNOWN_MODULE);
    }

    @Test
    @DisplayName("plano vazio é acusado")
    void emptyPlan() {
        assertThat(TrainingPlan.validate(
                new TrainingPlan.ModulePlan(TrainingPlan.MOD_TI, List.of())))
                .anyMatch(f -> f.problem() == TrainingPlan.Problem.EMPTY_PLAN);
    }
}
