package br.com.ziipsan.reporting.exportprofile;

import br.com.ziipsan.reporting.indicator.IndicatorClosing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um ponto da serie historica de exportacao: nasce SOMENTE de um
 * fechamento CONGELADO da P3.41 ({@link IndicatorClosing} com status
 * CLOSED). Fechamento reaberto nao e serie historica exportavel - o
 * valor vigente passa a ser o do fechamento seguinte.
 */
public record SeriesPoint(LocalDate periodStart, LocalDate periodEnd, BigDecimal value, int formulaVersion) {

    public SeriesPoint {
        if (periodStart == null || periodEnd == null) {
            throw new ExportValidationException("EXPORT_PERIOD_REQUIRED", "ponto de serie sem periodo");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new ExportValidationException("EXPORT_PERIOD_INVALID",
                    "fim " + periodEnd + " anterior ao inicio " + periodStart);
        }
        if (value == null) {
            throw new ExportValidationException("EXPORT_VALUE_REQUIRED", "ponto de serie sem valor");
        }
        if (formulaVersion < 1) {
            throw new ExportValidationException("EXPORT_FORMULA_VERSION_INVALID",
                    "versao de formula deve ser >= 1, recebida: " + formulaVersion);
        }
    }

    public static SeriesPoint fromClosing(IndicatorClosing closing) {
        if (closing == null) {
            throw new ExportValidationException("EXPORT_CLOSING_REQUIRED", "serie exige fechamento da P3.41");
        }
        if (closing.status() != IndicatorClosing.Status.CLOSED) {
            throw new ExportValidationException("EXPORT_CLOSING_NOT_CLOSED",
                    "fechamento de " + closing.indicatorCode() + " em " + closing.periodStart()
                            + " nao esta CLOSED (status " + closing.status() + "): serie historica "
                            + "de exportacao so carrega valores congelados");
        }
        return new SeriesPoint(closing.periodStart(), closing.periodEnd(), closing.value(),
                closing.formulaVersion());
    }
}
