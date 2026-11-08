package br.com.ziipsan.compliance.lgpd.privacyiso;

import br.com.ziipsan.compliance.lgpd.privacy.PrivacyAvailability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-74: pacote ligado por cert.iso27701.enabled com escopo e
 * edição; 2025 alvo, 2019 só em transição; desligar não apaga registro
 * nem afeta a P3.40.
 */
class Iso27701ActivationTest {

    private static Iso27701Activation target() {
        return Iso27701Activation.enabled("2025", Iso27701Activation.EditionUse.TARGET,
                "Escopo declarado da ativação", "SGPI cobre o atendimento ao titular");
    }

    @Test
    @DisplayName("edição 2025 como alvo, com os dois escopos, ativa o pacote")
    void targetEditionActivates() {
        assertThat(target().enabled()).isTrue();
        assertThat(target().edition()).isEqualTo("2025");
    }

    @Test
    @DisplayName("edição 2019 só em transição: como alvo é recusada; em transição é aceita")
    void edition2019OnlyInTransition() {
        assertThatThrownBy(() -> Iso27701Activation.enabled("2019",
                Iso27701Activation.EditionUse.TARGET, "escopo", "sgpi"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Iso27701Activation.enabled("2019", Iso27701Activation.EditionUse.TRANSITION,
                "escopo", "sgpi").enabled()).isTrue();
    }

    @Test
    @DisplayName("edição 2025 declarada em transição é incoerente e recusada")
    void edition2025AsTransitionRefused() {
        assertThatThrownBy(() -> Iso27701Activation.enabled("2025",
                Iso27701Activation.EditionUse.TRANSITION, "escopo", "sgpi"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("edição fora do parâmetro cert.iso27701.edition é recusada")
    void unknownEditionRefused() {
        assertThatThrownBy(() -> Iso27701Activation.enabled("2030",
                Iso27701Activation.EditionUse.TARGET, "escopo", "sgpi"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem escopo declarado ou sem escopo do SGPI, a ativação é recusada")
    void scopesAreRequired() {
        assertThatThrownBy(() -> Iso27701Activation.enabled("2025",
                Iso27701Activation.EditionUse.TARGET, " ", "sgpi"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iso27701Activation.enabled("2025",
                Iso27701Activation.EditionUse.TARGET, "escopo", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("norma desligada barra o nascimento de registro do pacote")
    void disabledBlocksRegistration() {
        assertThatThrownBy(() -> Iso27701Activation.disabled().requireEnabled())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("desligar a ED-74 não afeta a P3.40: privacidade do titular continua ativa")
    void disablingDoesNotAffectP340() {
        Iso27701Activation off = Iso27701Activation.disabled();
        assertThat(off.enabled()).isFalse();
        assertThat(PrivacyAvailability.isActive(Set.of(), Map.of("cert.iso27701.enabled", "false")))
                .isTrue();
    }
}
