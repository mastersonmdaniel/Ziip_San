package br.com.ziipsan.reporting.export;

import br.com.ziipsan.reporting.registry.ReportDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** CSV RFC 4180: aspas dobradas, campos com separador/aspas/quebra entre aspas. */
public final class CsvReportWriter implements ReportWriter {

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public byte[] write(ReportDefinition definition, List<Map<String, Object>> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        List<String> names = definition.columns().stream().map(c -> c.name()).toList();
        out.append(String.join(",", names.stream().map(CsvReportWriter::escape).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            List<String> cells = names.stream()
                    .map(name -> escape(ReportWriter.cellText(row.get(name)))).toList();
            out.append(String.join(",", cells)).append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
