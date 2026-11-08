package br.com.ziipsan.core.certification.water46001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Iso46001ActivationTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test @DisplayName("ligada: edicao 2019, escopo e instalacao declarados, proposta != aceite")
    void enabledOk() {
        var act = Iso46001Activation.enabled("Uso proprio nas instalacoes operacionais", "ETA-CENTRAL", A, B);
        assertThat(act.enabled()).isTrue();
        assertThat(act.edition()).isEqualTo("2019");
        assertThat(act.installationScopeRef()).isEqualTo("ETA-CENTRAL");
        act.requireEnabled();
        act.requireInstallationInScope("ETA-CENTRAL");
    }

    @Test @DisplayName("projeto da edicao 2 NAO e adotavel como edicao da ativacao")
    void draftEdition2NotAdoptable() {
        assertThatThrownBy(() -> new Iso46001Activation(true, "2", "escopo", "ETA-CENTRAL", A, B))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("edicao 2");
        assertThatThrownBy(() -> new Iso46001Activation(true, "2026", "escopo", "ETA-CENTRAL", A, B))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("sem escopo de instalacao ou sem escopo declarado, nao liga")
    void scopeRequired() {
        assertThatThrownBy(() -> Iso46001Activation.enabled("escopo", " ", A, B))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("instalacao");
        assertThatThrownBy(() -> Iso46001Activation.enabled(null, "ETA-CENTRAL", A, B))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scopeStatement");
    }

    @Test @DisplayName("proposta e aceite da mesma pessoa recusam (ADR-0041)")
    void proposerEqualsAcceptor() {
        assertThatThrownBy(() -> Iso46001Activation.enabled("escopo", "ETA-CENTRAL", A, A))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pessoas diferentes");
    }

    @Test @DisplayName("desligada: nenhum registro nasce; instalacao fora do escopo recusa")
    void disabledAndOutOfScope() {
        assertThatThrownBy(() -> Iso46001Activation.disabled().requireEnabled())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("desligado");
        var act = Iso46001Activation.enabled("escopo", "ETA-CENTRAL", A, B);
        assertThatThrownBy(() -> act.requireInstallationInScope("ETE-NORTE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora do escopo");
    }

    @Test @DisplayName("parametros reais do core V3 nomeados")
    void paramNames() {
        assertThat(Iso46001Activation.ENABLED_PARAM).isEqualTo("cert.iso46001.enabled");
        assertThat(Iso46001Activation.EDITION_PARAM).isEqualTo("cert.iso46001.edition");
    }
}
