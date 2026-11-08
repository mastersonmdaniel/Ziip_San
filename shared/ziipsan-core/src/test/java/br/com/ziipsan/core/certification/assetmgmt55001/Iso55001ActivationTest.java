package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Iso55001ActivationTest {

    @Test
    @DisplayName("edição-alvo é 2024, a única do parâmetro do core")
    void targetEdition() {
        assertThat(Iso55001Activation.TARGET_EDITION).isEqualTo("2024");
        assertThat(Iso55001Fixtures.on().edition()).isEqualTo("2024");
        assertThatThrownBy(() -> new Iso55001Activation(true, "2014", "escopo",
                Iso55001Fixtures.PROPOSER, Iso55001Fixtures.ACCEPTER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2024");
    }

    @Test
    @DisplayName("ligada exige escopo declarado e proposta != aceite")
    void enabledRequirements() {
        assertThatThrownBy(() -> Iso55001Activation.enabled(" ", Iso55001Fixtures.PROPOSER,
                Iso55001Fixtures.ACCEPTER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso55001Activation.enabled("escopo", Iso55001Fixtures.PROPOSER,
                Iso55001Fixtures.PROPOSER)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pessoas diferentes");
    }

    @Test
    @DisplayName("desligada: requireEnabled barra nascimento de registro")
    void disabledGate() {
        assertThatThrownBy(() -> Iso55001Activation.disabled().requireEnabled())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("desligado");
    }
}
