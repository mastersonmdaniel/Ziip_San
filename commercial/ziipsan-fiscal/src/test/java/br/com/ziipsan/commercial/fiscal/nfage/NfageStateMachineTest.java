package br.com.ziipsan.commercial.fiscal.nfage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Máquina de estados da NFAg-e: os 9 estados do dicionário (DAD-18), as transições do
 * ciclo de vida (incl. substituição -> REPLACED, cancelamento por evento e
 * inutilização -> VOIDED) e a recusa de toda transição fora da tabela.
 */
class NfageStateMachineTest {

    @Test
    @DisplayName("o dicionário tem exatamente os 9 estados da spec")
    void exactlyNineStates() {
        assertThat(NfageStatus.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "RESERVED", "SIGNED", "TRANSMITTED", "AUTHORIZED", "REJECTED",
                "PENDING_RECONCILIATION", "CANCELLED", "REPLACED", "VOIDED");
    }

    @Test
    @DisplayName("transições legais do ciclo completo, da substituição, do cancelamento e da inutilização")
    void legalTransitions() {
        assertThat(NfageStateMachine.canTransition(NfageStatus.RESERVED, NfageStatus.SIGNED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.SIGNED, NfageStatus.TRANSMITTED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.TRANSMITTED, NfageStatus.AUTHORIZED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.TRANSMITTED, NfageStatus.REJECTED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.TRANSMITTED, NfageStatus.PENDING_RECONCILIATION)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.PENDING_RECONCILIATION, NfageStatus.AUTHORIZED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.PENDING_RECONCILIATION, NfageStatus.REJECTED)).isTrue();
        // Evento de cancelamento e substituição só sobre documento autorizado.
        assertThat(NfageStateMachine.canTransition(NfageStatus.AUTHORIZED, NfageStatus.CANCELLED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.AUTHORIZED, NfageStatus.REPLACED)).isTrue();
        // Inutilização: número reservado/assinado e nunca transmitido.
        assertThat(NfageStateMachine.canTransition(NfageStatus.RESERVED, NfageStatus.VOIDED)).isTrue();
        assertThat(NfageStateMachine.canTransition(NfageStatus.SIGNED, NfageStatus.VOIDED)).isTrue();
    }

    @Test
    @DisplayName("transições ilegais são recusadas, inclusive qualquer saída de estado terminal")
    void illegalTransitions() {
        assertThat(NfageStateMachine.canTransition(NfageStatus.RESERVED, NfageStatus.AUTHORIZED)).isFalse();
        assertThat(NfageStateMachine.canTransition(NfageStatus.RESERVED, NfageStatus.TRANSMITTED)).isFalse();
        assertThat(NfageStateMachine.canTransition(NfageStatus.SIGNED, NfageStatus.AUTHORIZED)).isFalse();
        assertThat(NfageStateMachine.canTransition(NfageStatus.RESERVED, NfageStatus.CANCELLED)).isFalse();
        assertThat(NfageStateMachine.canTransition(NfageStatus.TRANSMITTED, NfageStatus.CANCELLED)).isFalse();
        assertThat(NfageStateMachine.canTransition(NfageStatus.AUTHORIZED, NfageStatus.SIGNED)).isFalse();
        for (NfageStatus terminal : new NfageStatus[]{
                NfageStatus.REJECTED, NfageStatus.CANCELLED, NfageStatus.REPLACED, NfageStatus.VOIDED}) {
            assertThat(NfageStateMachine.isTerminal(terminal)).isTrue();
            for (NfageStatus destino : NfageStatus.values()) {
                assertThat(NfageStateMachine.canTransition(terminal, destino)).isFalse();
            }
        }
        assertThatThrownBy(() -> NfageStateMachine.requireTransition(
                NfageStatus.RESERVED, NfageStatus.AUTHORIZED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RESERVED");
    }

    @Test
    @DisplayName("documentos vivos são os 5 estados que a idempotência e a unicidade de origem protegem")
    void liveStates() {
        assertThat(NfageStateMachine.isLive(NfageStatus.RESERVED)).isTrue();
        assertThat(NfageStateMachine.isLive(NfageStatus.SIGNED)).isTrue();
        assertThat(NfageStateMachine.isLive(NfageStatus.TRANSMITTED)).isTrue();
        assertThat(NfageStateMachine.isLive(NfageStatus.AUTHORIZED)).isTrue();
        assertThat(NfageStateMachine.isLive(NfageStatus.PENDING_RECONCILIATION)).isTrue();
        assertThat(NfageStateMachine.isLive(NfageStatus.REJECTED)).isFalse();
        assertThat(NfageStateMachine.isLive(NfageStatus.CANCELLED)).isFalse();
        assertThat(NfageStateMachine.isLive(NfageStatus.REPLACED)).isFalse();
        assertThat(NfageStateMachine.isLive(NfageStatus.VOIDED)).isFalse();
    }
}
