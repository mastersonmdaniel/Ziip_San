package br.com.ziipsan.core.certification.infosec27001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-73: pacote ligado por cert.iso27001.enabled com escopo
 * declarado (cert.scope_statement) e edição (cert.iso27001.edition);
 * edição-alvo ISO/IEC 27001:2022 com a Emenda 1:2024.
 */
class Iso27001ActivationTest {

    private static final UUID PROPOSER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ACCEPTOR = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    @DisplayName("edição-alvo 2022 com Emenda 1:2024 e escopo declarado ativa a norma")
    void targetEditionActivates() {
        Iso27001Activation a = Iso27001Activation.enabled("SGSI da plataforma SYN", PROPOSER, ACCEPTOR);
        assertThat(a.enabled()).isTrue();
        assertThat(a.edition()).isEqualTo("2022");
        assertThat(a.amendment()).isEqualTo("1:2024");
        a.requireEnabled();
    }

    @Test
    @DisplayName("edição fora do parâmetro cert.iso27001.edition é recusada")
    void unknownEditionRefused() {
        assertThatThrownBy(() -> new Iso27001Activation(true, "2013", "1:2024", "escopo",
                PROPOSER, ACCEPTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Iso27001Activation(true, "2022", "2:2025", "escopo",
                PROPOSER, ACCEPTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem escopo declarado, a ativação é recusada")
    void scopeIsRequired() {
        assertThatThrownBy(() -> Iso27001Activation.enabled(" ", PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso27001Activation.enabled(null, PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("proposta e aceite pela mesma pessoa são recusados")
    void samePersonProposalAndAcceptanceRefused() {
        assertThatThrownBy(() -> Iso27001Activation.enabled("escopo", PROPOSER, PROPOSER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pessoas diferentes");
    }

    @Test
    @DisplayName("sem proponente ou sem aceitante, a ativação é recusada")
    void proposerAndAcceptorAreRequired() {
        assertThatThrownBy(() -> Iso27001Activation.enabled("escopo", null, ACCEPTOR))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Iso27001Activation.enabled("escopo", PROPOSER, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("norma desligada barra o nascimento de registro do pacote")
    void disabledBlocksRegistration() {
        assertThatThrownBy(() -> Iso27001Activation.disabled().requireEnabled())
                .isInstanceOf(IllegalStateException.class);
    }
}
