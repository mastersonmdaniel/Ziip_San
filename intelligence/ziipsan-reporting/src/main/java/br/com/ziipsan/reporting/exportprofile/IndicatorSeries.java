package br.com.ziipsan.reporting.exportprofile;

import br.com.ziipsan.reporting.sheet.IndicatorSheet;

import java.util.Comparator;
import java.util.List;

/**
 * A serie historica de UMA ficha da ED-77 ({@link IndicatorSheet}) para
 * exportacao: pontos ordenados por inicio de periodo, sem periodos
 * repetidos e NUNCA vazia - o acceptance pede "indicadores e serie
 * historica"; exportar indicador sem serie e recusa nomeada.
 */
public record IndicatorSeries(IndicatorSheet sheet, List<SeriesPoint> points) {

    public IndicatorSeries {
        if (sheet == null) {
            throw new ExportValidationException("EXPORT_SHEET_REQUIRED",
                    "serie sem ficha da ED-77: os perfis nascem das fichas de indicador");
        }
        if (points == null || points.isEmpty()) {
            throw new ExportValidationException("EXPORT_SERIES_EMPTY",
                    "indicador " + sheet.code() + " sem serie historica: exportacao recusada");
        }
        points = points.stream().sorted(Comparator.comparing(SeriesPoint::periodStart)).toList();
        long distinct = points.stream().map(SeriesPoint::periodStart).distinct().count();
        if (distinct != points.size()) {
            throw new ExportValidationException("EXPORT_SERIES_DUPLICATED_PERIOD",
                    "indicador " + sheet.code() + " com periodo repetido na serie historica");
        }
    }

    public String code() { return sheet.code(); }
}
