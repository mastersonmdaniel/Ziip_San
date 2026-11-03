package br.com.ziipsan.core.parameters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P0.19: quem propõe não aprova, efeito só futuro, vigência sem sobreposição, aceite com hash. */
class ParameterVersionRuleTest {

    private static final UUID PROPOSER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();

    @Test
    @DisplayName("quem propõe não aprova o próprio parâmetro")
    void proposerCannotApprove() {
        assertThatThrownBy(() -> ParameterVersionRule.validateApproval(
                PROPOSER, PROPOSER, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não aprova");
    }

    @Test
    @DisplayName("valid_from anterior à aprovação é recusado (efeito só futuro); igual, aceito")
    void futureEffectOnly() {
        assertThatThrownBy(() -> ParameterVersionRule.validateApproval(
                PROPOSER, APPROVER, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 9)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> ParameterVersionRule.validateApproval(
                PROPOSER, APPROVER, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("vigência sobreposta é recusada pelo validador (o banco recusa pelo EXCLUDE)")
    void overlappingValidityRefused() {
        var candidate = new ParameterVersionRule.Validity(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        var existing = List.of(new ParameterVersionRule.Validity(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 15)));
        assertThatThrownBy(() -> ParameterVersionRule.validateNoOverlap(candidate, existing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobreposta");
        var adjacent = new ParameterVersionRule.Validity(
                LocalDate.of(2026, 3, 1), null);
        // Intervalos com fim incluso: 01/03 em diante não intersecta [01/01, 15/02].
        assertThatCode(() -> ParameterVersionRule.validateNoOverlap(adjacent, existing))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("operação sem valor aceito e vigente falha com erro explícito que nomeia a chave")
    void missingParameterErrorNamesKey() {
        var error = new MissingParameterException("fee.fine_pct", "nenhuma versão vigente em 2026-01-15");
        assertThatThrownBy(() -> {
            throw error;
        }).isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("fee.fine_pct");
    }

    @Test
    @DisplayName("aceite com hash divergente da versão é recusado")
    void acceptanceHashMustMatch() {
        assertThatThrownBy(() -> ParameterVersionRule.validateAcceptanceHash("a".repeat(64), "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> ParameterVersionRule.validateAcceptanceHash("a".repeat(64), "a".repeat(64)))
                .doesNotThrowAnyException();
    }
}
