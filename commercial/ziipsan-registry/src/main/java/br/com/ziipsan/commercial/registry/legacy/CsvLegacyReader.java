package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor CSV do legado (P1.2).
 *
 * <p>Primeira linha não vazia é o cabeçalho (nomes aparados, sem aspas).
 * Campos entre aspas duplas aceitam o delimitador dentro e aspas escapadas
 * (""). Linhas em branco são ignoradas. Linha com MAIS campos que o
 * cabeçalho é erro (quase sempre delimitador errado no arquivo); linha com
 * MENOS campos completa com null. O delimitador vem do mapeamento
 * (legados brasileiros usam ';' com frequência).
 */
public final class CsvLegacyReader implements LegacySourceReader {

    private final String csv;
    private final char delimiter;

    public CsvLegacyReader(String csv, char delimiter) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("O conteúdo CSV está vazio.");
        }
        this.csv = csv;
        this.delimiter = delimiter;
    }

    @Override
    public List<LegacyRow> read() {
        List<String> lines = splitLines(csv);
        int headerIndex = -1;
        List<String> header = null;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                header = parseLine(lines.get(i));
                headerIndex = i;
                break;
            }
        }
        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException("O CSV não tem cabeçalho.");
        }
        List<String> columns = new ArrayList<>();
        for (String column : header) {
            String name = column == null ? "" : column.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("O cabeçalho do CSV tem coluna sem nome.");
            }
            columns.add(name);
        }
        List<LegacyRow> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = parseLine(line);
            if (fields.size() > columns.size()) {
                throw new IllegalArgumentException("Linha " + (i + 1) + " do CSV tem mais campos que o cabeçalho.");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                values.put(columns.get(c), c < fields.size() ? fields.get(c) : null);
            }
            // Map.copyOf rejeita null — e campo ausente É null por contrato.
            rows.add(new LegacyRow(i + 1, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        }
        return List.copyOf(rows);
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                current.append(c);
                continue;
            }
            if (!quoted && (c == '\n' || c == '\r')) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                lines.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        lines.add(current.toString());
        return lines;
    }

    private List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Aspas sem fechamento na linha do CSV: " + line);
        }
        fields.add(current.toString());
        return fields;
    }
}
