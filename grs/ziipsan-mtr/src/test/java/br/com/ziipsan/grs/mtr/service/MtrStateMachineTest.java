package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.grs.mtr.domain.WasteMtr.MtrStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static br.com.ziipsan.grs.mtr.domain.WasteMtr.MtrStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtrStateMachineTest {

    /** A tabela esperada, escrita à mão, independente da implementação. */
    private static final Map<MtrStatus, Set<MtrStatus>> EXPECTED = Map.of(
            EMITTED, Set.of(COLLECTED, CANCELLED, EXPIRED),
            COLLECTED, Set.of(IN_TRANSIT, RECEIVED, CANCELLED),
            IN_TRANSIT, Set.of(RECEIVED, CANCELLED),
            RECEIVED, Set.of(DESTINED, REJECTED),
            DESTINED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of(),
            EXPIRED, Set.of());

    @Test
    @DisplayName("as 64 combinações de origem e destino batem com a tabela esperada")
    void everyPair() {
        for (MtrStatus from : MtrStatus.values()) {
            for (MtrStatus to : MtrStatus.values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                assertThat(MtrStateMachine.canTransition(from, to))
                        .as("%s -> %s", from, to).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("regressão: COLLECTED -> RECEIVED é permitido (antes receiveMtr falhava sempre)")
    void collectedCanBeReceived() {
        MtrStateMachine.require(COLLECTED, RECEIVED);
    }

    @Test
    @DisplayName("o fluxo completo EMITTED -> COLLECTED -> RECEIVED -> DESTINED é válido")
    void happyPath() {
        MtrStateMachine.require(EMITTED, COLLECTED);
        MtrStateMachine.require(COLLECTED, RECEIVED);
        MtrStateMachine.require(RECEIVED, DESTINED);
    }

    @Test
    @DisplayName("não é possível destinar sem receber, nem voltar atrás")
    void invalidJumps() {
        assertThatThrownBy(() -> MtrStateMachine.require(EMITTED, DESTINED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMITTED -> DESTINED");
        assertThatThrownBy(() -> MtrStateMachine.require(COLLECTED, EMITTED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MtrStateMachine.require(EMITTED, RECEIVED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("DESTINED, REJECTED, CANCELLED e EXPIRED são terminais")
    void terminalStates() {
        assertThat(EnumSet.of(DESTINED, REJECTED, CANCELLED, EXPIRED))
                .allMatch(MtrStateMachine::isTerminal);
        assertThat(EnumSet.of(EMITTED, COLLECTED, IN_TRANSIT, RECEIVED))
                .noneMatch(MtrStateMachine::isTerminal);
        assertThat(MtrStateMachine.allowedFrom(DESTINED)).isEmpty();
    }
}
