package br.com.ziipsan.core.certification.env14001;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentalEmergencyScenarioTest {

    @Test
    void scenarioByScenarioWithActionsAndResponsible() {
        EnvironmentalEmergencyScenario s = EnvironmentalEmergencyScenario.declare(
                TestSupport.enabledTarget(), TestSupport.id(), TestSupport.TENANT,
                "SYN-CEN-01", "SYN-vazamento de produto quimico na ETA",
                List.of("SYN-conter o vazamento", "SYN-isolar a area"),
                TestSupport.ACTOR, List.of("WTP-CHEMICAL-HANDLING"));
        assertThat(s.responseActions()).hasSize(2);
        assertThat(s.linkedAspectCodes()).containsExactly("WTP-CHEMICAL-HANDLING");
        assertThat(s.reviewed()).isFalse();
    }

    @Test
    void emptyPreparationRefused() {
        assertThatThrownBy(() -> EnvironmentalEmergencyScenario.declare(
                TestSupport.enabledTarget(), TestSupport.id(), TestSupport.TENANT,
                "SYN-CEN-02", "SYN-sem acao", List.of(), TestSupport.ACTOR, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preparacao vazia");
        assertThatThrownBy(() -> new EnvironmentalEmergencyScenario(TestSupport.disabled(),
                TestSupport.id(), TestSupport.TENANT, "SYN-CEN-03", "SYN-d",
                List.of("SYN-acao"), TestSupport.ACTOR, List.of(), null))
                .isInstanceOf(IllegalStateException.class);
    }
}
