package br.com.ziipsan.platform.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3/PLT-22 - defesa contra automacao: WCAG 2.2 3.3.8, stuffing, senha vazada, limite por e-mail. */
class BotDefensePolicyTest {

    @Test
    void desafioNuncaECognitivo() {
        assertThat(BotDefensePolicy.ALLOWED_KINDS).containsExactlyInAnyOrder(
                BotDefensePolicy.ChallengeKind.NONE,
                BotDefensePolicy.ChallengeKind.PROOF_OF_WORK,
                BotDefensePolicy.ChallengeKind.INVISIBLE);
    }

    @Test
    void modosDaDefesa() {
        var signals = new BotDefensePolicy.StuffingSignals(true, false);
        var none = new BotDefensePolicy.StuffingSignals(false, false);
        assertThat(BotDefensePolicy.requiredChallenge(PortalParameters.BotDefenseMode.OFF, signals))
                .isEqualTo(BotDefensePolicy.ChallengeKind.NONE);
        assertThat(BotDefensePolicy.requiredChallenge(PortalParameters.BotDefenseMode.PASSIVE, signals))
                .isEqualTo(BotDefensePolicy.ChallengeKind.NONE);
        assertThat(BotDefensePolicy.requiredChallenge(PortalParameters.BotDefenseMode.CHALLENGE, signals))
                .isEqualTo(BotDefensePolicy.ChallengeKind.PROOF_OF_WORK);
        assertThat(BotDefensePolicy.requiredChallenge(PortalParameters.BotDefenseMode.CHALLENGE, none))
                .isEqualTo(BotDefensePolicy.ChallengeKind.INVISIBLE);
    }

    @Test
    void deteccaoDePreenchimentoDeCredenciais() {
        var thresholds = new BotDefensePolicy.StuffingThresholds(20, 10);
        assertThat(BotDefensePolicy.detectStuffing(21, 1, thresholds).manyDocumentsFromOneIp()).isTrue();
        assertThat(BotDefensePolicy.detectStuffing(1, 11, thresholds).manyIpsForOneDocument()).isTrue();
        assertThat(BotDefensePolicy.detectStuffing(2, 2, thresholds).any()).isFalse();
        assertThat(BotDefensePolicy.detectStuffing(21, 1, thresholds).telemetryEvent())
                .isEqualTo("PORTAL_CREDENTIAL_STUFFING_SUSPECT");
    }

    @Test
    void senhaVazadaECurtaSaoRecusadasESemVerificadorOCadastroFalhaFechado() {
        BotDefensePolicy.BreachedPasswordPort port = pw -> pw.equals("senha-vazada-123");
        assertThatThrownBy(() -> BotDefensePolicy.validateNewPassword("senha-vazada-123", port))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BotDefensePolicy.validateNewPassword("curta", port))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BotDefensePolicy.validateNewPassword("uma-senha-boa-qualquer", null))
                .isInstanceOf(IllegalStateException.class);
        BotDefensePolicy.validateNewPassword("uma-senha-boa-qualquer", port);
    }

    @Test
    void limiteDeCriacaoDeContaPorEmail() {
        var throttle = new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15);
        assertThat(BotDefensePolicy.accountCreationAllowed(2, throttle)).isTrue();
        assertThat(BotDefensePolicy.accountCreationAllowed(3, throttle)).isFalse();
    }
}
