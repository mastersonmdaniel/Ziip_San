package br.com.ziipsan.commercial.registry.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1.40: leitor de CSV do importador genérico. Sem dependência de biblioteca:
 * o formato do modelo é fixo (cabeçalho + linhas, vírgula como separador,
 * aspas duplas com escape por duplicação, CRLF ou LF), e o parser recusa o
 * que não consegue ler em vez de adivinhar.
 *
 * <p>Regras: a primeira linha não vazia é o cabeçalho; linhas vazias no fim
 * são ignoradas; uma linha com número de campos diferente do cabeçalho é
 * recusa de arquivo inteiro (o relatório marca a linha 0); valores são
 * aparados nas pontas (o modelo é texto, não posição). Bytes NUL ou texto
 * que não seja UTF-8 válido são recusados antes do parse, pelo serviço —
 * aqui só chega String.
 */
public final class ImportCsvParser {

    private ImportCsvParser() {
    }

    /** Arquivo lido: cabeçalho e linhas de dados (número da linha = posição, 1 = primeira). */
    public record ParsedCsv(List<String> header, List<ParsedRow> rows) {
    }

    /** Uma linha de dados: número (1 = primeira após o cabeçalho) e valores por coluna. */
    public record ParsedRow(int number, Map<String, String> values) {
    }

    /** Falha de leitura do arquivo inteiro (não de uma linha). */
    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }

    public static ParsedCsv parse(String content) {
        if (content == null || content.isBlank()) {
            throw new ParseException("Arquivo vazio: envie o modelo preenchido.");
        }
        List<List<String>> records = splitRecords(content);
        // Ignora linhas vazias no fim (o editor costuma deixar uma).
        while (!records.isEmpty() && records.get(records.size() - 1).stream().allMatch(String::isBlank)) {
            records.remove(records.size() - 1);
        }
        if (records.isEmpty()) {
            throw new ParseException("Arquivo vazio: envie o modelo preenchido.");
        }
        List<String> header = records.get(0).stream().map(String::strip).toList();
        if (header.stream().anyMatch(String::isBlank)) {
            throw new ParseException("Cabecalho com coluna vazia.");
        }
        if (header.size() != header.stream().distinct().count()) {
            throw new ParseException("Cabecalho com coluna repetida.");
        }
        List<ParsedRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> fields = records.get(i);
            if (fields.size() != header.size()) {
                throw new ParseException("Linha " + i + " tem " + fields.size()
                        + " campos e o cabecalho tem " + header.size() + ".");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) {
                values.put(header.get(j), fields.get(j).strip());
            }
            rows.add(new ParsedRow(i, Map.copyOf(values)));
        }
        return new ParsedCsv(List.copyOf(header), List.copyOf(rows));
    }

    /**
     * Divide o texto em registros, respeitando aspas: vírgula dentro de aspas
     * não separa, quebra de linha dentro de aspas não termina o registro, e
     * aspas duplicadas dentro de aspas são uma aspa literal (RFC 4180).
     */
    static List<List<String>> splitRecords(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
            } else if (c == '"') {
                inQuotes = true;
                i++;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
                i++;
            } else if (c == '\r') {
                // CRLF ou CR isolado: termina o registro.
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
                i++;
            } else if (c == '\n') {
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
                i++;
            } else {
                field.append(c);
                i++;
            }
        }
        if (inQuotes) {
            throw new ParseException("Aspas sem fechamento no arquivo.");
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }
}
