package br.com.ziipsan.core.certification.env14001;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Iso14001ActivationTest {

    @Test
    void targetEdition2026Activates() {
        Iso14001Activation a = TestSupport.enabledTarget();
        assertThat(a.enabled()).isTrue();
        assertThat(a.edition()).isEqualTo("2026");
        assertThat(a.transition()).isFalse();
        a.requireEnabled();
    }

    @Test
    void edition2015OnlyInTransition() {
        Iso14001Activation a = TestSupport.enabledTransition();
        assertThat(a.edition()).isEqualTo("2015");
        assertThat(a.transition()).isTrue();
        assertThatThrownBy(() -> new Iso14001Activation(true, "2015", false,
                TestSupport.SCOPE, TestSupport.PROPOSER, TestSupport.ACCEPTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transicao");
    }

    @Test
    void transitionFlagRefusedForTargetEdition() {
        // Correcao D2 da revisao da ED-70: transition=true com a
        // edicao-alvo 2026 e incoerencia semantica — transicao so
        // existe fora da edicao-alvo (na edicao de 2015).
        assertThatThrownBy(() -> new Iso14001Activation(true, "2026", true,
                TestSupport.SCOPE, TestSupport.PROPOSER, TestSupport.ACCEPTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transicao");
    }

    @Test
    void unknownEditionRefused() {
        assertThatThrownBy(() -> new Iso14001Activation(true, "2004", false,
                TestSupport.SCOPE, TestSupport.PROPOSER, TestSupport.ACCEPTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopeAndDistinctPeopleRequired() {
        assertThatThrownBy(() -> Iso14001Activation.enabledTarget(" ", TestSupport.PROPOSER,
                TestSupport.ACCEPTER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso14001Activation.enabledTarget(TestSupport.SCOPE,
                TestSupport.PROPOSER, TestSupport.PROPOSER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pessoas diferentes");
    }

    @Test
    void disabledRefusesBirthButKeepsReadingPossible() {
        assertThatThrownBy(() -> TestSupport.disabled().requireEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cert.iso14001.enabled");
    }

    @Test
    void paramNamesMatchCoreV3() {
        assertThat(Iso14001Activation.ENABLED_PARAM).isEqualTo("cert.iso14001.enabled");
        assertThat(Iso14001Activation.EDITION_PARAM).isEqualTo("cert.iso14001.edition");
        assertThat(Iso14001Activation.TARGET_EDITION).isEqualTo("2026");
        assertThat(Iso14001Activation.TRANSITION_EDITION).isEqualTo("2015");
    }
}
