package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Iso45001ActivationTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    @DisplayName("ativação válida: edição-alvo 2018, escopo e proposta != aceite")
    void validActivation() {
        Iso45001Activation act = Iso45001Activation.enabled("SYN-escopo", A, B);
        assertThat(act.enabled()).isTrue();
        assertThat(act.edition()).isEqualTo("2018");
        act.requireEnabled();
    }

    @Test
    @DisplayName("outra edição é recusada (o parâmetro do core V3 só admite 2018)")
    void otherEditionRefused() {
        assertThatThrownBy(() -> new Iso45001Activation(true, "2025", "SYN", A, B))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("escopo em branco com a norma ligada é recusado")
    void blankScopeRefused() {
        assertThatThrownBy(() -> Iso45001Activation.enabled("  ", A, B))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("proposta e aceite pela mesma pessoa são recusados")
    void samePersonRefused() {
        assertThatThrownBy(() -> Iso45001Activation.enabled("SYN", A, A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pessoas diferentes");
    }

    @Test
    @DisplayName("desligada: requireEnabled lança e nenhum registro nasce")
    void disabledRefuses() {
        Iso45001Activation off = Iso45001Activation.disabled();
        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(off::requireEnabled).isInstanceOf(IllegalStateException.class);
    }
}
