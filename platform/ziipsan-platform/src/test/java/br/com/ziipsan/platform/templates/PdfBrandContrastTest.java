package br.com.ziipsan.platform.templates;

import br.com.ziipsan.platform.brand.BrandContrast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Contraste do PDF (P2.17, MARCA-E-TEMAS §3.4) com vetores CALCULADOS À
 * MÃO pela fórmula WCAG 2.1 (luminância relativa com linearização sRGB):
 * L = 0,2126·R + 0,7152·G + 0,0722·B sobre os canais linearizados;
 * razão = (Lclara + 0,05) / (Lescura + 0,05).
 * <ul>
 *   <li>preto #000000: L=0; branco #FFFFFF: L=1 → 1,05/0,05 = 21,0;</li>
 *   <li>#0369A0: canais 3,105,160 → lin 0,00091 / 0,14108 / 0,35148 →
 *       L=0,12661 → 1,05/0,17661 = 5,9454 sobre o branco;</li>
 *   <li>#0F172A: L=0,00882 → 1,05/0,05882 = 17,8525 sobre o branco, e
 *       0,17661/0,05882 = 3,0027 contra #0369A0;</li>
 *   <li>#767676: L=0,18116 → 1,05/0,23116 = 4,5422 (passa, fronteira);</li>
 *   <li>#777777: L=0,18448 → 1,05/0,23448 = 4,4781 (não passa);</li>
 *   <li>#FF0000: L=0,2126 → 1,05/0,2626 = 3,9985 (não passa);
 *       #0000FF: L=0,0722 → 1,05/0,1222 = 8,5925 (passa);</li>
 *   <li>#CCCCCC escurecido pelo ajuste do §3.4: primeiro fator que atinge
 *       4,5:1 é 58% → #767676 (4,5422).</li>
 * </ul>
 */
class PdfBrandContrastTest {

    private static TenantPdfBrand brandWithColor(String color) {
        return new TenantPdfBrand(UUID.randomUUID(), "Prestador", null, color,
                null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("vetores de razão calculados à mão: extremos e primária do produto")
    void handCalculatedRatios() {
        assertThat(BrandContrast.ratio("#000000", "#FFFFFF")).isCloseTo(21.0, within(0.001));
        assertThat(BrandContrast.ratio("#FFFFFF", "#FFFFFF")).isCloseTo(1.0, within(0.001));
        assertThat(BrandContrast.ratio("#0369A0", "#FFFFFF")).isCloseTo(5.9454, within(0.001));
        assertThat(BrandContrast.ratio("#0F172A", "#FFFFFF")).isCloseTo(17.8525, within(0.01));
        assertThat(BrandContrast.ratio("#0369A0", "#0F172A")).isCloseTo(3.0027, within(0.001));
    }

    @Test
    @DisplayName("fronteira 4,5:1 sobre o papel: #767676 passa (4,5422) e #777777 não passa (4,4781)")
    void boundaryOnPaper() {
        assertThat(BrandContrast.ratio("#767676", PdfBrandContrast.PAPER))
                .isCloseTo(4.5422, within(0.001));
        assertThat(BrandContrast.ratio("#777777", PdfBrandContrast.PAPER))
                .isCloseTo(4.4781, within(0.001));
        PdfBrandContrast.Assessment passing = PdfBrandContrast.assess(brandWithColor("#767676"));
        assertThat(passing.printable()).isTrue();
        assertThat(passing.adjusted()).isFalse();
        assertThat(passing.effectiveTextColor()).isEqualTo("#767676");
        // #777777 está abaixo: a cor EFETIVA é a escurecida pelo §3.4, e a
        // razão da efetiva volta a passar — nunca a cor pedida em silêncio.
        PdfBrandContrast.Assessment adjusted = PdfBrandContrast.assess(brandWithColor("#777777"));
        assertThat(adjusted.printable()).isTrue();
        assertThat(adjusted.adjusted()).isTrue();
        assertThat(adjusted.ratioRequestedOnPaper()).isCloseTo(4.4781, within(0.001));
        assertThat(adjusted.ratioEffectiveOnPaper()).isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
    }

    @Test
    @DisplayName("cores puras sobre o papel: vermelho reprova (3,9985), azul aprova (8,5925)")
    void pureColorsOnPaper() {
        assertThat(BrandContrast.ratio("#FF0000", PdfBrandContrast.PAPER))
                .isCloseTo(3.9985, within(0.001));
        assertThat(BrandContrast.ratio("#0000FF", PdfBrandContrast.PAPER))
                .isCloseTo(8.5925, within(0.001));
        assertThat(PdfBrandContrast.assess(brandWithColor("#FF0000")).adjusted()).isTrue();
        assertThat(PdfBrandContrast.assess(brandWithColor("#0000FF")).adjusted()).isFalse();
    }

    @Test
    @DisplayName("cinza claro #CCCCCC é escurecido até #767676, o primeiro tom com 4,5:1")
    void lightGrayIsDarkenedToBoundary() {
        PdfBrandContrast.Assessment assessment = PdfBrandContrast.assess(brandWithColor("#CCCCCC"));
        assertThat(assessment.adjusted()).isTrue();
        assertThat(assessment.effectiveTextColor()).isEqualTo("#767676");
        assertThat(assessment.ratioEffectiveOnPaper()).isCloseTo(4.5422, within(0.001));
    }

    @Test
    @DisplayName("texto sobre a cor da marca: branco na primária (5,9454 > 3,0027), escuro no amarelo")
    void textOnBrandColor() {
        assertThat(BrandContrast.textOnColor("#0369A0")).isEqualTo("#FFFFFF");
        PdfBrandContrast.Assessment primary = PdfBrandContrast.assess(brandWithColor("#0369A0"));
        assertThat(primary.textOnBrandColor()).isEqualTo("#FFFFFF");
        assertThat(primary.ratioTextOnBrandColor()).isCloseTo(5.9454, within(0.001));
        assertThat(BrandContrast.textOnColor("#FFFF00")).isEqualTo("#0F172A");
    }

    @Test
    @DisplayName("propriedade do §3.4: para toda cor de marca, o texto sobre ela atinge 4,5:1")
    void textOnColorAlwaysReachesMinimum() {
        for (String color : new String[]{"#0369A0", "#FF0000", "#00FF00", "#0000FF",
                "#FFFF00", "#CCCCCC", "#767676", "#123456", "#FEDCBA"}) {
            PdfBrandContrast.Assessment assessment = PdfBrandContrast.assess(brandWithColor(color));
            assertThat(assessment.printable()).as("cor " + color).isTrue();
            assertThat(assessment.ratioTextOnBrandColor())
                    .as("texto sobre " + color)
                    .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
            assertThat(assessment.ratioEffectiveOnPaper())
                    .as("cor efetiva de " + color + " sobre o papel")
                    .isGreaterThanOrEqualTo(BrandContrast.MIN_RATIO);
        }
    }

    @Test
    @DisplayName("marca sem cor não viola o §3.4: imprimível, sem cor efetiva")
    void brandWithoutColorIsPrintable() {
        PdfBrandContrast.Assessment assessment = PdfBrandContrast.assess(brandWithColor(null));
        assertThat(assessment.printable()).isTrue();
        assertThat(assessment.effectiveTextColor()).isNull();
    }
}
