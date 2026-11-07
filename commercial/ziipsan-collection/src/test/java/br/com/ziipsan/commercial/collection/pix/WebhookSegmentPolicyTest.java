package br.com.ziipsan.commercial.collection.pix;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.2 (correcao pos-revisao M2) - politica de entropia do segmento do
 * webhook: a geracao e por CSPRNG e o piso de validacao recusa o segmento
 * fraco obvio. A politica nao prova entropia; ela registra a exigencia e
 * barra o previsivel.
 */
class WebhookSegmentPolicyTest {

    @Test
    void gerarProduzSegmentoValidoEDistinto() {
        String first = WebhookSegmentPolicy.generate();
        String second = WebhookSegmentPolicy.generate();
        assertThat(first).hasSize(WebhookSegmentPolicy.GENERATED_LENGTH);
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy(first)).isTrue();
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy(second)).isTrue();
        assertThat(first).isNotEqualTo(second);
        assertThatCode(() -> WebhookSegmentPolicy.requireValid(first)).doesNotThrowAnyException();
    }

    @Test
    void segmentosFracosSaoRecusados() {
        // Um so caractere repetido (passaria no CHECK de formato da V6_3).
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")).isFalse();
        // Padrao curto repetido: poucos caracteres distintos.
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy("abcdabcdabcdabcdabcdabcdabcdabcd")).isFalse();
        // Curto demais, vazio e nulo.
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy("curto")).isFalse();
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy("")).isFalse();
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy(null)).isFalse();
        assertThatThrownBy(() -> WebhookSegmentPolicy.requireValid("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segmento");
    }

    @Test
    void segmentoForteConhecidoEAceito() {
        // O segmento usado nos testes do callback: 32 caracteres, 12 distintos.
        assertThat(WebhookSegmentPolicy.hasRequiredEntropy("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4")).isTrue();
    }
}
