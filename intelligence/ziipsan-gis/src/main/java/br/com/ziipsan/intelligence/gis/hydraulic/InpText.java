package br.com.ziipsan.intelligence.gis.hydraulic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Formatação de números/tempos de .inp, independente de locale (mesma disciplina do InpWriter da P2.21). */
public final class InpText {

    private InpText() {
    }

    public static String num(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    public static String num(BigDecimal v, int scale) {
        return v == null ? "0" : num(v.setScale(scale, RoundingMode.HALF_UP));
    }

    public static String num(double v) {
        return num(BigDecimal.valueOf(v));
    }

    public static String oneLine(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** ID válido para EPANET/SWMM: sem espaço, ';' ou aspas, até 31 caracteres (limite do EPANET). */
    public static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= 31
                && id.chars().noneMatch(ch -> Character.isWhitespace(ch) || ch == ';' || ch == '"');
    }

    /** Segundos para H:MM:SS. */
    public static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
