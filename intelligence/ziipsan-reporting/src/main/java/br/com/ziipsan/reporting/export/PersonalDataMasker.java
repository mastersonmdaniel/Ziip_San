package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportColumn;
import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.spi.ReportUserContext.MaskCeiling;

/**
 * Mascara pelo teto do usuario (ADR-0008) - a mesma da tela, como manda a
 * regra unica da P1.12. Aplica-se a toda coluna marcada {@code personal} na
 * definicao e a toda coluna do tipo DOCUMENT, sem excecao: exportacao sem
 * mascara nao existe neste nucleo.
 */
public final class PersonalDataMasker {

    private PersonalDataMasker() {
    }

    public static boolean needsMask(ReportColumn column) {
        return column.personal() || column.type() == ReportColumnType.DOCUMENT;
    }

    public static Object mask(ReportColumn column, Object value, MaskCeiling ceiling) {
        if (value == null || !needsMask(column)) {
            return value;
        }
        String text = String.valueOf(value);
        return switch (ceiling) {
            case UNMASKED -> text;
            case FULL -> "***";
            case PARTIAL -> partial(text);
        };
    }

    /** Parcial: preserva so os 2 ultimos caracteres (documento ***.***.789-00
     *  na tela equivale a manter o fim; aqui, generico e sem formato). */
    static String partial(String text) {
        String digits = text.replaceAll("\\D", "");
        String base = digits.length() >= 4 ? digits : text;
        if (base.length() <= 2) {
            return "***";
        }
        return "***" + base.substring(base.length() - 2);
    }
}
