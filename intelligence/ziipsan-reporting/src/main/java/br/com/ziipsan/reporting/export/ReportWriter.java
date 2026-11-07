package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportDefinition;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Escritor de formato. Recebe linhas ja mascaradas; nunca acessa o banco. */
public interface ReportWriter {

    ExportFormat format();

    byte[] write(ReportDefinition definition, List<Map<String, Object>> rows) throws IOException;

    /** Celula como texto de exibicao (formatacao unica dos tres formatos). */
    static String cellText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}
