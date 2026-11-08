package br.com.ziipsan.platform.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingCompletionGateTest {

    private static final UUID USER_1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID USER_2 = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000201");

    private static TrainingCompletion.ModuleCompletion concluded(String moduleKey) {
        TrainingPlan.ModulePlan plan = TrainingPlan.standardPlan(moduleKey);
        Set<String> units = new java.util.HashSet<>();
        for (TrainingPlan.Unit u : plan.units()) {
            units.add(u.key());
        }
        return new TrainingCompletion.ModuleCompletion(moduleKey, units, 40,
                List.of(new TrainingCompletion.Participant(USER_1, true),
                        new TrainingCompletion.Participant(USER_2, true)),
                LocalDate.of(2026, 11, 20));
    }

    private static PreTrainingTenant.Spec syntheticTenant() {
        return new PreTrainingTenant.Spec(TENANT, PreTrainingTenant.Source.SYNTHETIC_MASS,
                true, 0, false, null, null);
    }

    private static ItEnablement.ItCompletion itComplete() {
        Set<String> delivered = new java.util.HashSet<>();
        Set<String> handsOn = new java.util.HashSet<>();
        for (ItEnablement.Topic t : ItEnablement.TOPICS) {
            if (t.mandatory()) {
                delivered.add(t.key());
                if (t.handsOn()) {
                    handsOn.add(t.key());
                }
            }
        }
        return new ItEnablement.ItCompletion(delivered, handsOn);
    }

    @Test
    @DisplayName("módulo com tudo entregue, 40 h e participantes concluídos conclui")
    void moduleConcludes() {
        TrainingPlan.ModulePlan plan = TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO);
        assertThat(TrainingCompletion.evaluate(plan,
                concluded(TrainingPlan.MOD_CADASTRO)).concluded()).isTrue();
    }

    @Test
    @DisplayName("unidade faltando, hora cortada e participante pendente reprovam, nomeados")
    void failuresAreNamed() {
        TrainingPlan.ModulePlan plan = TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO);
        TrainingCompletion.ModuleCompletion partial = new TrainingCompletion.ModuleCompletion(
                TrainingPlan.MOD_CADASTRO,
                Set.of(TrainingPlan.UNIT_FUNDAMENTOS), 39,
                List.of(new TrainingCompletion.Participant(USER_1, true),
                        new TrainingCompletion.Participant(USER_2, false)),
                LocalDate.of(2026, 11, 20));
        TrainingCompletion.Result r = TrainingCompletion.evaluate(plan, partial);
        assertThat(r.concluded()).isFalse();
        assertThat(r.failures()).anyMatch(f -> f.contains(TrainingPlan.UNIT_OPERACAO_GUIADA));
        assertThat(r.failures()).anyMatch(f -> f.contains("39"));
        assertThat(r.failures()).anyMatch(f -> f.contains(USER_2.toString()));
    }

    @Test
    @DisplayName("módulo sem nenhum participante não conclui")
    void noParticipants() {
        TrainingPlan.ModulePlan plan = TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO);
        TrainingCompletion.ModuleCompletion empty = new TrainingCompletion.ModuleCompletion(
                TrainingPlan.MOD_CADASTRO,
                Set.of(TrainingPlan.UNIT_FUNDAMENTOS, TrainingPlan.UNIT_OPERACAO_GUIADA,
                        TrainingPlan.UNIT_PRATICA, TrainingPlan.UNIT_EXCECOES,
                        TrainingPlan.UNIT_AVALIACAO),
                40, List.of(), LocalDate.of(2026, 11, 20));
        assertThat(TrainingCompletion.evaluate(plan, empty).concluded()).isFalse();
    }

    @Test
    @DisplayName("gate fecha com escopo completo, tenant pronto e TI completa")
    void gatePasses() {
        List<String> scope = List.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.MOD_TI);
        TrainingGate.Assessment a = TrainingGate.assess(scope,
                Map.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO),
                        TrainingPlan.MOD_TI, TrainingPlan.standardPlan(TrainingPlan.MOD_TI)),
                Map.of(TrainingPlan.MOD_CADASTRO, concluded(TrainingPlan.MOD_CADASTRO),
                        TrainingPlan.MOD_TI, concluded(TrainingPlan.MOD_TI)),
                syntheticTenant(), itComplete(), false);
        assertThat(a.trainingConcluded()).isTrue();
        assertThat(a.blockers()).isEmpty();
        assertThat(TrainingGate.journeyStepKey()).isEqualTo("TRAINING");
    }

    @Test
    @DisplayName("gate bloqueia sem o módulo da TI no escopo e sem registro de um módulo")
    void gateBlocks() {
        TrainingGate.Assessment noTi = TrainingGate.assess(
                List.of(TrainingPlan.MOD_CADASTRO),
                Map.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO)),
                Map.of(TrainingPlan.MOD_CADASTRO, concluded(TrainingPlan.MOD_CADASTRO)),
                syntheticTenant(), null, false);
        assertThat(noTi.trainingConcluded()).isFalse();
        assertThat(noTi.blockers()).anyMatch(b -> b.contains(TrainingPlan.MOD_TI));

        TrainingGate.Assessment missing = TrainingGate.assess(
                List.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.MOD_TI),
                Map.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO),
                        TrainingPlan.MOD_TI, TrainingPlan.standardPlan(TrainingPlan.MOD_TI)),
                Map.of(TrainingPlan.MOD_CADASTRO, concluded(TrainingPlan.MOD_CADASTRO)),
                syntheticTenant(), itComplete(), false);
        assertThat(missing.trainingConcluded()).isFalse();
        assertThat(missing.blockers())
                .anyMatch(b -> b.contains("sem registro de conclusão"));
    }

    @Test
    @DisplayName("gate bloqueia com o tenant de pré-treinamento não pronto, mesmo com módulos concluídos")
    void gateBlocksOnTenant() {
        PreTrainingTenant.Spec dirty = new PreTrainingTenant.Spec(TENANT,
                PreTrainingTenant.Source.SYNTHETIC_MASS, true, 2, false, null, null);
        TrainingGate.Assessment a = TrainingGate.assess(
                List.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.MOD_TI),
                Map.of(TrainingPlan.MOD_CADASTRO, TrainingPlan.standardPlan(TrainingPlan.MOD_CADASTRO),
                        TrainingPlan.MOD_TI, TrainingPlan.standardPlan(TrainingPlan.MOD_TI)),
                Map.of(TrainingPlan.MOD_CADASTRO, concluded(TrainingPlan.MOD_CADASTRO),
                        TrainingPlan.MOD_TI, concluded(TrainingPlan.MOD_TI)),
                dirty, itComplete(), false);
        assertThat(a.trainingConcluded()).isFalse();
        assertThat(a.blockers()).anyMatch(b -> b.contains("pré-treinamento"));
    }

    @Test
    @DisplayName("escopo vazio não conclui gate nenhum")
    void emptyScope() {
        TrainingGate.Assessment a = TrainingGate.assess(List.of(), Map.of(), Map.of(),
                syntheticTenant(), null, false);
        assertThat(a.trainingConcluded()).isFalse();
    }
}
