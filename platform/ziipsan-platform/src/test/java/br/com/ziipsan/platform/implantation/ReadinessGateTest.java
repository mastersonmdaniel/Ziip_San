package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessGateTest {

    private static ReadinessGate.JourneyState state(String... doneKeys) {
        Map<String, ReadinessGate.StepStatus> map = new HashMap<>();
        for (String key : doneKeys) {
            map.put(key, ReadinessGate.StepStatus.DONE);
        }
        return new ReadinessGate.JourneyState(map);
    }

    @Test
    @DisplayName("todos os obrigatórios concluídos: pronto para produção, 100%")
    void allDoneReady() {
        ReadinessGate.Assessment a = ReadinessGate.assess(
                state(ImplantationPlan.MANDATORY_STEP_KEYS.toArray(new String[0])));
        assertThat(a.readyForProduction()).isTrue();
        assertThat(a.percentComplete()).isEqualTo(100);
        assertThat(a.pendingMandatorySteps()).isEmpty();
        assertThat(ReadinessGate.productionBlockers(
                state(ImplantationPlan.MANDATORY_STEP_KEYS.toArray(new String[0])))).isEmpty();
    }

    @Test
    @DisplayName("jornada vazia: nada pronto, todos os obrigatórios pendentes")
    void emptyJourney() {
        ReadinessGate.Assessment a = ReadinessGate.assess(state());
        assertThat(a.readyForProduction()).isFalse();
        assertThat(a.percentComplete()).isEqualTo(0);
        assertThat(a.pendingMandatorySteps())
                .containsExactlyElementsOf(ImplantationPlan.MANDATORY_STEP_KEYS);
    }

    @Test
    @DisplayName("passo de vertical desconhecido não conta nem atrapalha; PENDING não é DONE")
    void unknownAndPendingSteps() {
        Map<String, ReadinessGate.StepStatus> map = new HashMap<>();
        map.put("VERTICAL_EXTRA_STEP", ReadinessGate.StepStatus.DONE);
        map.put(ImplantationPlan.STEP_PARAMETERS, ReadinessGate.StepStatus.PENDING);
        ReadinessGate.Assessment a = ReadinessGate.assess(new ReadinessGate.JourneyState(map));
        assertThat(a.completedMandatory()).isEqualTo(0);
        assertThat(a.pendingMandatorySteps()).contains(ImplantationPlan.STEP_PARAMETERS);
        assertThat(ReadinessGate.productionBlockers(new ReadinessGate.JourneyState(map)))
                .anyMatch(b -> b.contains(ImplantationPlan.STEP_PARAMETERS));
    }
}
