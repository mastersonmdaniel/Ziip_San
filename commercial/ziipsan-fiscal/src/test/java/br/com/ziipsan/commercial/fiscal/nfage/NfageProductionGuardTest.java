package br.com.ziipsan.commercial.fiscal.nfage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava tripla de produção: PRODUCTION só passa com habilitação explícita E tpAmb=1 E
 * contexto não-test-only — cada perna da trava é derrubada uma a uma.
 */
class NfageProductionGuardTest {

    private static NfageProductionGuard.ContextoAmbiente ctx(
            NfageProductionGuard.Ambiente ambiente, Integer tpAmb, boolean habilitada, boolean testOnly) {
        return new NfageProductionGuard.ContextoAmbiente(ambiente, tpAmb, habilitada, testOnly);
    }

    @Test
    @DisplayName("produção com as três pernas satisfeitas é permitida")
    void productionAllowed() {
        assertThatCode(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.PRODUCTION, 1, true, false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("produção sem habilitação explícita é recusada")
    void productionWithoutEnabling() {
        assertThatThrownBy(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.PRODUCTION, 1, false, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("habilitação");
    }

    @Test
    @DisplayName("produção com tpAmb de homologação (2) é incoerente e recusada")
    void productionWithHomologationTpAmb() {
        assertThatThrownBy(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.PRODUCTION, 2, true, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tpAmb=1");
    }

    @Test
    @DisplayName("produção a partir de contexto somente-teste é recusada mesmo com habilitação")
    void productionFromTestOnlyContext() {
        assertThatThrownBy(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.PRODUCTION, 1, true, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("test");
    }

    @Test
    @DisplayName("homologação exige tpAmb=2 e não exige habilitação de produção (é o aceite externo da P2.6)")
    void homologationRules() {
        assertThatCode(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.HOMOLOGATION, 2, false, true)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.HOMOLOGATION, 1, false, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tpAmb=2");
    }

    @Test
    @DisplayName("simulado nunca aceita tpAmb de produção")
    void simulatedRules() {
        assertThatCode(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.SIMULATED, 2, false, true)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> NfageProductionGuard.exigirPermitido(
                ctx(NfageProductionGuard.Ambiente.SIMULATED, 1, false, true)))
                .isInstanceOf(IllegalStateException.class);
    }
}
