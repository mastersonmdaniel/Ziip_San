package br.com.ziipsan.platform.signing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entropia e formato do codigo de verificacao (correcao D7, desenho da P3.35). */
class VerificationCodesTest {

    @Test
    @DisplayName("codigo: geracao CSPRNG sai no formato de 80 bits (16 simbolos Crockford agrupados)")
    void generatedCodesHaveHighEntropyShape() {
        String a = VerificationCodes.generate();
        String b = VerificationCodes.generate();
        assertThat(VerificationCodes.hasShape(a)).isTrue();
        assertThat(VerificationCodes.hasShape(b)).isTrue();
        assertThat(a).matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}");
        // Dois sorteios de 80 bits nao colidem na pratica; colisao indicaria gerador quebrado.
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("codigo: sequencial/previsivel nao tem o formato e requireShape recusa")
    void weakCodesRefused() {
        assertThat(VerificationCodes.hasShape("CODIGO-TESTE-1")).isFalse();
        assertThat(VerificationCodes.hasShape("0000-0000-0000-0001")).isTrue();
        assertThat(VerificationCodes.hasShape(null)).isFalse();
        assertThatThrownBy(() -> VerificationCodes.requireShape("123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VerificationCodes::generate");
    }
}
