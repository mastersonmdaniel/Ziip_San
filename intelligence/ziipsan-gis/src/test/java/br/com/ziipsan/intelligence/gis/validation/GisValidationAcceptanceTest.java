package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GisValidationAcceptance: a regra pura do aceite do painel (§5.5) —
 * justificativa obrigatória e só problema aberto aceita.
 */
class GisValidationAcceptanceTest {

    @Test
    @DisplayName("justificativa válida é aparada e devolvida")
    void justificationIsTrimmed() {
        assertThat(GisValidationAcceptance.requireJustification("  rede legada, DN abaixo do mínimo  "))
                .isEqualTo("rede legada, DN abaixo do mínimo");
    }

    @Test
    @DisplayName("justificativa ausente ou em branco é recusada como validação")
    void blankJustificationIsRejected() {
        assertThatThrownBy(() -> GisValidationAcceptance.requireJustification(null))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> GisValidationAcceptance.requireJustification("   "))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("só problema OPEN pode ser aceito")
    void onlyOpenCanBeAccepted() {
        GisValidationAcceptance.requireOpen("OPEN");
        assertThatThrownBy(() -> GisValidationAcceptance.requireOpen("ACCEPTED"))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> GisValidationAcceptance.requireOpen("RESOLVED"))
                .isInstanceOf(ApiException.Conflict.class);
    }
}
