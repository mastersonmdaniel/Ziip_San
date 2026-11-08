package br.com.ziipsan.commercial.fiscal.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Inutilização (P2.33): só faixa de numeração NÃO usada; um número usado recusa a faixa inteira. */
class VoidingRuleTest {

    @Test
    @DisplayName("faixa sem nenhum número usado é inutilizável")
    void unusedRangeAccepted() {
        // Usados: 10, 11, 20. Faixa 12..19 não toca nenhum — conferido à mão.
        assertThat(VoidingRule.isVoidable(12, 19, Set.of(10L, 11L, 20L))).isTrue();
        VoidingRule.requireVoidable(12, 19, Set.of(10L, 11L, 20L));
    }

    @Test
    @DisplayName("faixa contendo número usado é recusada, nomeando o número")
    void rangeWithUsedNumberRejected() {
        assertThatThrownBy(() -> VoidingRule.requireVoidable(12, 21, Set.of(10L, 11L, 20L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20");
        assertThat(VoidingRule.isVoidable(12, 21, Set.of(20L))).isFalse();
    }

    @Test
    @DisplayName("faixa invertida ou com início menor que 1 é recusada")
    void invalidRanges() {
        assertThatThrownBy(() -> VoidingRule.requireVoidable(19, 12, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoidingRule.requireVoidable(0, 5, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("número já inutilizado (VOIDED) continua usado: a porta o inclui no conjunto e a regra recusa reinutilizar")
    void voidedNumberStaysUsed() {
        // O conjunto de usados da porta inclui VOIDED por contrato (FiscalDocumentReadPort);
        // aqui, 15 presente no conjunto recusa a faixa que o contém.
        assertThat(VoidingRule.isVoidable(14, 16, Set.of(15L))).isFalse();
    }
}
