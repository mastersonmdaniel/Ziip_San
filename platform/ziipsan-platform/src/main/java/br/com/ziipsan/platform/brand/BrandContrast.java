package br.com.ziipsan.platform.brand;

import java.util.regex.Pattern;

/**
 * Contraste WCAG 2.1 da cor da marca do tenant (MARCA-E-TEMAS §3.4), aplicado
 * ao salvar (P1.19) e ao exibir a marca pré-login.
 *
 * <p>Regras do §3.4, sem inventar número: texto sobre a cor do tenant é
 * branco ou o texto escuro padrão, o que der 4,5:1 (um dos dois sempre dá);
 * a cor do tenant como texto sobre branco só vale com 4,5:1 — abaixo disso,
 * o sistema escurece a cor e avisa ao salvar. O "texto escuro padrão" aqui
 * é o token {@code foreground} do tema claro ({@code 222 47% 11%} em HSL,
 * que corresponde a {@code #0F172A}); o branco é {@code #FFFFFF}.
 *
 * <p>Classe pura, sem banco e sem Spring: os testes fixam os vetores
 * (a primária do produto {@code #0369a0} passa sem ajuste; um cinza claro
 * é escurecido até 4,5:1).
 */
public final class BrandContrast {

    /** Razão mínima WCAG AA para texto normal. */
    public static final double MIN_RATIO = 4.5;

    /** Texto escuro padrão: o {@code foreground} do tema claro. */
    public static final String DARK_TEXT = "#0F172A";

    /** Texto claro. */
    public static final String LIGHT_TEXT = "#FFFFFF";

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private BrandContrast() {
    }

    /** Resultado do ajuste de uma cor ao salvar. */
    public record AdjustedColor(String requested, String saved, boolean adjusted, String textOnColor) {
    }

    /** {@code true} quando a cor está no formato {@code #RRGGBB}. */
    public static boolean isHexColor(String color) {
        return color != null && HEX.matcher(color).matches();
    }

    /**
     * Razão de contraste WCAG entre duas cores {@code #RRGGBB}
     * (1,0 = idênticas; 21,0 = preto sobre branco).
     */
    public static double ratio(String first, String second) {
        double l1 = luminance(first);
        double l2 = luminance(second);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /**
     * Texto sobre a cor do tenant: branco ou o texto escuro padrão, o que
     * der a maior razão (um dos dois sempre dá 4,5:1, pelo §3.4).
     */
    public static String textOnColor(String color) {
        requireHex(color);
        return ratio(LIGHT_TEXT, color) >= ratio(DARK_TEXT, color) ? LIGHT_TEXT : DARK_TEXT;
    }

    /**
     * Ajuste ao salvar: se a cor como texto sobre branco já dá 4,5:1, ela
     * volta intacta; senão, é escurecida (canais multiplicados por um fator
     * decrescente, preservando o matiz) até dar 4,5:1 — no limite, preto,
     * que dá 21:1. O chamador avisa o usuário quando {@code adjusted}.
     */
    public static AdjustedColor adjustForWhiteBackground(String color) {
        requireHex(color);
        String normalized = color.toUpperCase();
        if (ratio(normalized, LIGHT_TEXT) >= MIN_RATIO) {
            return new AdjustedColor(normalized, normalized, false, textOnColor(normalized));
        }
        int r = Integer.parseInt(normalized.substring(1, 3), 16);
        int g = Integer.parseInt(normalized.substring(3, 5), 16);
        int b = Integer.parseInt(normalized.substring(5, 7), 16);
        // Passo de 1%: 100 tentativas bastam para ir de qualquer cor a preto;
        // o laço termina no mais tardar em fator 0 (preto, 21:1).
        for (int pct = 99; pct >= 0; pct--) {
            double factor = pct / 100.0;
            String darkened = String.format("#%02X%02X%02X",
                    (int) Math.round(r * factor), (int) Math.round(g * factor), (int) Math.round(b * factor));
            if (ratio(darkened, LIGHT_TEXT) >= MIN_RATIO) {
                return new AdjustedColor(normalized, darkened, true, textOnColor(darkened));
            }
        }
        return new AdjustedColor(normalized, "#000000", true, LIGHT_TEXT);
    }

    /** Luminância relativa WCAG de uma cor {@code #RRGGBB}. */
    static double luminance(String color) {
        requireHex(color);
        double r = linearize(Integer.parseInt(color.substring(1, 3), 16) / 255.0);
        double g = linearize(Integer.parseInt(color.substring(3, 5), 16) / 255.0);
        double b = linearize(Integer.parseInt(color.substring(5, 7), 16) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void requireHex(String color) {
        if (!isHexColor(color)) {
            throw new IllegalArgumentException("cor fora do formato #RRGGBB: " + color);
        }
    }
}
