package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-69: pacote ligado por cert.iso9001.enabled com escopo
 * declarado (cert.scope_statement) e edição (cert.iso9001.edition);
 * proposta e aceite por pessoas diferentes; 2026 alvo, 2015 só em
 * transição; desligar não apaga registro.
 */
class Iso9001ActivationTest {

    private static final UUID PROPOSER = UUID.randomUUID();
    private static final UUID ACCEPTOR = UUID.randomUUID();

    private static Iso9001Activation target() {
        return Iso9001Activation.enabled("2026", Iso9001Activation.EditionUse.TARGET,
                "Escopo declarado da ativação", PROPOSER, ACCEPTOR);
    }

    @Test
    @DisplayName("edição 2026 como alvo, com escopo e proposta≠aceite, ativa o pacote")
    void targetEditionActivates() {
        assertThat(target().enabled()).isTrue();
        assertThat(target().edition()).isEqualTo("2026");
    }

    @Test
    @DisplayName("edição 2015 só em transição: como alvo é recusada; em transição é aceita")
    void edition2015OnlyInTransition() {
        assertThatThrownBy(() -> Iso9001Activation.enabled("2015",
                Iso9001Activation.EditionUse.TARGET, "escopo", PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Iso9001Activation.enabled("2015", Iso9001Activation.EditionUse.TRANSITION,
                "escopo", PROPOSER, ACCEPTOR).enabled()).isTrue();
    }

    @Test
    @DisplayName("edição 2026 declarada em transição é incoerente e recusada")
    void edition2026AsTransitionRefused() {
        assertThatThrownBy(() -> Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TRANSITION, "escopo", PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("edição fora do parâmetro cert.iso9001.edition é recusada")
    void unknownEditionRefused() {
        assertThatThrownBy(() -> Iso9001Activation.enabled("2030",
                Iso9001Activation.EditionUse.TARGET, "escopo", PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem escopo declarado, a ativação é recusada")
    void scopeIsRequired() {
        assertThatThrownBy(() -> Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, " ", PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, null, PROPOSER, ACCEPTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("proposta e aceite pela mesma pessoa são recusados")
    void samePersonProposalAndAcceptanceRefused() {
        assertThatThrownBy(() -> Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", PROPOSER, PROPOSER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pessoas diferentes");
    }

    @Test
    @DisplayName("sem proponente ou sem aceitante, a ativação é recusada")
    void proposerAndAcceptorAreRequired() {
        assertThatThrownBy(() -> Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", null, ACCEPTOR))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", PROPOSER, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("norma desligada barra o nascimento de registro do pacote")
    void disabledBlocksRegistration() {
        assertThatThrownBy(() -> Iso9001Activation.disabled().requireEnabled())
                .isInstanceOf(IllegalStateException.class);
    }
}
