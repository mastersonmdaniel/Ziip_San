package br.com.ziipsan.commercial.registry.importer;

import java.util.List;

/**
 * P1.40: modelo para baixar, gerado do esquema — cabeçalho com as colunas na
 * ordem do esquema, mais uma linha de exemplo (os exemplos declarados nas
 * colunas). O usuário baixa, preenche e envia de volta; o cabeçalho devolvido
 * tem de ser exatamente este, ou o arquivo inteiro é recusado.
 */
public final class ImportTemplate {

    private ImportTemplate() {
    }

    public static String csv(ImportSchema schema) {
        List<ImportColumn> columns = schema.columns();
        StringBuilder out = new StringBuilder();
        out.append(join(columns.stream().map(ImportColumn::name).toList())).append("\r\n");
        out.append(join(columns.stream().map(ImportColumn::example).toList())).append("\r\n");
        return out.toString();
    }

    private static String join(List<String> values) {
        return row(values);
    }

    /** Uma linha CSV, com escape RFC 4180. */
    public static String row(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String value = values.get(i);
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                out.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                out.append(value);
            }
        }
        return out.toString();
    }
}
