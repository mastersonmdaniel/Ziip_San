package br.com.ziipsan.platform.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * P1.19 — vetores de contraste WCAG 2.1 da cor da marca (MARCA-E-TEMAS §3.4).
 */
class BrandContrastTest {

    @Test
    @DisplayName("preto sobre branco dá 21:1; branco sobre preto dá 21:1")
    void blackOnWhiteIs21() {
        assertThat(BrandContrast.ratio("#000000", "#FFFFFF")).isCloseTo(21.0, within(0.01));
        assertThat(BrandContrast.ratio("#FFFFFF", "#000000")).isCloseTo(21.0, within(0.01));
    }

    @Test
    @DisplayName("primária do produto passa sem ajuste e pede texto branco")
    void productPrimaryPassesUnchangedWithWhiteText() {
        BrandContrast.AdjustedColor adjusted = BrandContrast.adjustForWhiteBackground("#0369a0");
        assertThat(adjusted.adjusted()).isFalse();
        assertThat(adjusted.saved()).isEqualTo("#0369A0");
        assertThat(adjusted.textOnColor()).isEqualTo("#FFFFFF");
        assertThat(BrandContrast.textOnColor("#0369a0")).isEqualTo("#FFFFFF");
    }

    @Test
    @DisplayName("cinza claro é escurecido até 4,5:1 sobre branco, com matiz preservado")
    void lightGrayIsDarkenedToAA() {
        BrandContrast.AdjustedColor adjusted = BrandContrast.adjustForWhiteBackground("#F5F5F5");
        assertThat(adjusted.adjusted()).isTrue();
        assertThat(adjusted.saved()).isNotEqualTo("#F5F5F5");
        assertThat(BrandContrast.ratio(adjusted.saved(), "#FFFFFF")).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    @DisplayName("preto passa sem ajuste e pede texto branco; branco pede texto escuro")
    void blackAndWhiteTextChoice() {
        assertThat(BrandContrast.adjustForWhiteBackground("#000000").adjusted()).isFalse();
        assertThat(BrandContrast.textOnColor("#000000")).isEqualTo("#FFFFFF");
        assertThat(BrandContrast.textOnColor("#FFFFFF")).isEqualTo("#0F172A");
    }

    @Test
    @DisplayName("formato fora de #RRGGBB é recusado")
    void invalidHexRejected() {
        assertThat(BrandContrast.isHexColor("0369a0")).isFalse();
        assertThat(BrandContrast.isHexColor("#0369a")).isFalse();
        assertThat(BrandContrast.isHexColor(null)).isFalse();
        assertThatThrownBy(() -> BrandContrast.textOnColor("azul"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
