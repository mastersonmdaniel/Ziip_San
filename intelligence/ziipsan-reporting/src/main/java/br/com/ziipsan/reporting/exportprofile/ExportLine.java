package br.com.ziipsan.reporting.exportprofile;

import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;

import java.util.List;

/**
 * Linha de um documento de exportacao: o indicador da ficha da ED-77
 * (codigo, titulo, unidade, periodicidade e grau de confianca lidos da
 * definicao da P3.41 via ficha) + a serie historica. {@code externalReference}
 * so existe no perfil ISO_37120 (referencia declarada no mapeamento);
 * no perfil PNQS e sempre nula - o PNQS nao tem codigos proprios nesta
 * entrega e nenhum e inventado.
 */
public record ExportLine(String indicatorCode, String title, String unit,
                         IndicatorPeriodicity periodicity, ConfidenceGrade confidenceGrade,
                         String externalReference, List<SeriesPoint> points) {

    public ExportLine {
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new ExportValidationException("EXPORT_LINE_CODE_REQUIRED", "linha de exportacao sem indicador");
        }
        if (points == null || points.isEmpty()) {
            throw new ExportValidationException("EXPORT_LINE_SERIES_EMPTY",
                    "linha de " + indicatorCode + " sem serie historica");
        }
        points = List.copyOf(points);
    }
}
