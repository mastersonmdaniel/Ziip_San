package br.com.ziipsan.platform.templates;

import br.com.ziipsan.platform.brand.BrandContrast;

import java.util.ArrayList;
import java.util.List;

/**
 * Regras de contraste de MARCA-E-TEMAS §3.4 aplicadas ao PDF (P2.17).
 *
 * <p>O §3.4 (texto da P1.19 em {@link BrandContrast}): texto sobre a cor do
 * tenant é branco ({@code #FFFFFF}) ou o texto escuro padrão
 * ({@code #0F172A}), o que der maior razão — um dos dois sempre atinge
 * 4,5:1; e a cor do tenant COMO TEXTO sobre branco só vale com 4,5:1 —
 * abaixo disso o sistema escurece a cor ao salvar e avisa.
 *
 * <p>No PDF o fundo é o papel, {@link #PAPER} = {@code #FFFFFF}: é sobre
 * ele que a cor da marca aparece como texto (nome do tenant no cabeçalho)
 * e é ele o fundo do rodapé. Esta classe NÃO duplica a matemática: toda a
 * luminância/razão vem de {@link BrandContrast} (WCAG 2.1, luminância
 * relativa com linearização sRGB). O que ela acrescenta é a verificação do
 * documento: dado o instantâneo da marca, diz se ela pode ser impressa
 * como está e qual cor EFETIVA usar em cada papel:
 * <ul>
 *   <li>cor da marca como texto sobre o papel: precisa de
 *       {@link BrandContrast#MIN_RATIO}:1 contra {@link #PAPER}; se a cor
 *       gravada não atinge (marca anterior ao ajuste da P1.19, ou cor
 *       gravada por outro caminho), a cor efetiva é a escurecida por
 *       {@link BrandContrast#adjustForWhiteBackground} — a mesma regra do
 *       §3.4, nunca uma cor inventada por esta camada;</li>
 *   <li>faixa de cabeçalho na cor da marca: o texto sobre ela é
 *       {@link BrandContrast#textOnColor}, cuja razão contra a cor é
 *       sempre ≥ 4,5:1 (propriedade do §3.4, conferida por teste).</li>
 * </ul>
 *
 * <p>Vetores calculados à mão (fórmula WCAG, conferidos no teste):
 * preto × branco = 21,0; {@code #0369A0} (primária do produto) × branco =
 * 5,9454 (passa); {@code #767676} × branco = 4,5422 (passa, fronteira);
 * {@code #777777} × branco = 4,4781 (não passa); {@code #FF0000} × branco =
 * 3,9985 (não passa); {@code #0000FF} × branco = 8,5925 (passa);
 * {@code #0369A0} × {@code #0F172A} = 3,0027 (por isso o texto sobre a
 * primária é o branco, 5,9454, e não o escuro).
 */
public final class PdfBrandContrast {

    /** Fundo do documento: o papel. */
    public static final String PAPER = "#FFFFFF";

    private PdfBrandContrast() {
    }

    /** Resultado da verificação de uma marca para impressão. */
    public record Assessment(
            String requestedColor,
            String effectiveTextColor,
            boolean adjusted,
            double ratioRequestedOnPaper,
            double ratioEffectiveOnPaper,
            String textOnBrandColor,
            double ratioTextOnBrandColor,
            List<String> problems) {

        public Assessment {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }

        /** A marca pode ser impressa (com a cor efetiva, se ajustada)? */
        public boolean printable() {
            return problems.isEmpty();
        }
    }

    /**
     * Avalia a marca para o PDF. Marca sem cor não tem problema de
     * contraste (não há o que imprimir em cor) e devolve razões 0 com
     * {@code printable() == true} — ausência de cor não é violação do §3.4.
     * Cor fora de {@code #RRGGBB} é problema nomeado (nunca ignorada).
     */
    public static Assessment assess(TenantPdfBrand brand) {
        if (brand == null) {
            throw new IllegalArgumentException("brand e obrigatoria na avaliacao de contraste");
        }
        String color = brand.color();
        if (color == null) {
            return new Assessment(null, null, false, 0.0, 0.0, null, 0.0, List.of());
        }
        List<String> problems = new ArrayList<>();
        double ratioOnPaper = BrandContrast.ratio(color, PAPER);
        BrandContrast.AdjustedColor adjusted = BrandContrast.adjustForWhiteBackground(color);
        double ratioEffective = BrandContrast.ratio(adjusted.saved(), PAPER);
        if (ratioEffective < BrandContrast.MIN_RATIO) {
            // Inatingível pela construção do ajuste (preto dá 21:1); se um
            // dia for atingido, é defeito nomeado, não impressão silenciosa.
            problems.add("Cor da marca sem ajuste possivel para 4,5:1 sobre o papel: " + color);
        }
        String onColor = BrandContrast.textOnColor(adjusted.saved());
        double ratioOnColor = BrandContrast.ratio(onColor, adjusted.saved());
        if (ratioOnColor < BrandContrast.MIN_RATIO) {
            problems.add("Texto sobre a cor da marca abaixo de 4,5:1: " + onColor
                    + " sobre " + adjusted.saved());
        }
        return new Assessment(color, adjusted.saved(), adjusted.adjusted(),
                ratioOnPaper, ratioEffective, onColor, ratioOnColor, problems);
    }

    /** Problemas de contraste da marca para o PDF; vazio = imprimível. */
    public static List<String> problems(TenantPdfBrand brand) {
        return assess(brand).problems();
    }

    /** Aprova ou lança {@link IllegalArgumentException} com os problemas. */
    public static void assertPrintable(TenantPdfBrand brand) {
        List<String> problems = problems(brand);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Marca recusada para o PDF: "
                    + String.join("; ", problems));
        }
    }
}
