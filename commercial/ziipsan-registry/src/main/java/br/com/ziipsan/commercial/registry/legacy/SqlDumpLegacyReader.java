package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de dump SQL do legado (P1.2).
 *
 * <p>Entende o que um dump de cadastro realmente traz: sentenças
 * {@code INSERT INTO tabela (colunas) VALUES (...), (...);} — inclusive
 * multi-linha e multi-tupla. Qualquer outra sentença (CREATE, COPY, SET,
 * comentários) é ignorada, não erro: o dump inteiro pode ser passado sem
 * recorte prévio. Quando {@code table} é informado, só os INSERTs dessa
 * tabela (sem schema, case-insensitive) viram linhas; sem ele, valem todos.
 *
 * <p>Valores suportados: strings entre aspas simples ('' escapa aspas),
 * números, TRUE/FALSE e NULL. Expressões (funções, subselects) são
 * recusadas — um dump com expressão precisa de normalização antes, nunca
 * de avaliação silenciosa.
 */
public final class SqlDumpLegacyReader implements LegacySourceReader {

    private final String sql;
    private final String table;

    public SqlDumpLegacyReader(String sql, String table) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("O dump SQL está vazio.");
        }
        this.sql = sql;
        this.table = table == null || table.isBlank() ? null : table.trim();
    }

    @Override
    public List<LegacyRow> read() {
        List<LegacyRow> rows = new ArrayList<>();
        List<String> statements = splitStatements(sql);
        int rowNo = 0;
        for (String statement : statements) {
            ParsedInsert insert = parseInsert(statement);
            if (insert == null) {
                continue;
            }
            if (table != null && !insert.table().equalsIgnoreCase(table)) {
                continue;
            }
            for (List<String> tuple : insert.tuples()) {
                if (tuple.size() != insert.columns().size()) {
                    throw new IllegalArgumentException("Tupla do INSERT tem " + tuple.size()
                            + " valores para " + insert.columns().size() + " colunas.");
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < insert.columns().size(); i++) {
                    values.put(insert.columns().get(i), tuple.get(i));
                }
                // Map.copyOf rejeita null — e NULL do SQL É null por contrato.
                rows.add(new LegacyRow(++rowNo, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
            }
        }
        return List.copyOf(rows);
    }

    private record ParsedInsert(String table, List<String> columns, List<List<String>> tuples) {
    }

    /** Divide em sentenças por ';' fora de string e fora de comentário. */
    static List<String> splitStatements(String text) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                current.append(c);
                if (c == '\'') {
                    if (next == '\'') {
                        current.append(next);
                        i++;
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (c == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inString = true;
                current.append(c);
                continue;
            }
            if (c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    /** Faz o parse de um INSERT; devolve null para qualquer outra sentença. */
    static ParsedInsert parseInsert(String statement) {
        String text = statement.trim();
        if (text.length() < 12 || !text.regionMatches(true, 0, "INSERT INTO", 0, 11)) {
            return null;
        }
        int pos = 11;
        pos = skipWs(text, pos);
        // nome da tabela: [schema.]nome, com ou sem aspas duplas
        StringBuilder tableName = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '"') {
                int end = text.indexOf('"', pos + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Aspas sem fechamento no nome da tabela.");
                }
                tableName.append(text, pos + 1, end);
                pos = end + 1;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$') {
                tableName.append(c);
                pos++;
            } else {
                break;
            }
        }
        String fullTable = tableName.toString();
        String simpleTable = fullTable.contains(".")
                ? fullTable.substring(fullTable.lastIndexOf('.') + 1) : fullTable;
        pos = skipWs(text, pos);
        if (pos >= text.length() || text.charAt(pos) != '(') {
            throw new IllegalArgumentException("INSERT sem lista de colunas.");
        }
        List<String> columns = parseIdentifierList(text, pos);
        pos = skipToValues(text, pos);
        List<List<String>> tuples = new ArrayList<>();
        while (true) {
            pos = skipWs(text, pos);
            if (pos >= text.length() || text.charAt(pos) != '(') {
                throw new IllegalArgumentException("VALUES do INSERT sem tupla.");
            }
            int[] end = new int[1];
            tuples.add(parseTuple(text, pos, end));
            pos = end[0];
            pos = skipWs(text, pos);
            if (pos < text.length() && text.charAt(pos) == ',') {
                pos++;
                continue;
            }
            break;
        }
        return new ParsedInsert(simpleTable, columns, tuples);
    }

    private static int skipWs(String text, int pos) {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static List<String> parseIdentifierList(String text, int openParen) {
        int close = findCloseParen(text, openParen);
        String inside = text.substring(openParen + 1, close);
        List<String> columns = new ArrayList<>();
        for (String part : inside.split(",")) {
            String name = part.trim().replace("\"", "");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Coluna sem nome no INSERT.");
            }
            columns.add(name);
        }
        return columns;
    }

    private static int skipToValues(String text, int pos) {
        int close = findCloseParen(text, pos);
        int values = indexOfWord(text, "VALUES", close);
        if (values < 0) {
            throw new IllegalArgumentException("INSERT sem VALUES.");
        }
        return values + "VALUES".length();
    }

    private static int indexOfWord(String text, String word, int from) {
        for (int i = from; i + word.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, word, 0, word.length())) {
                boolean beforeOk = i == from || !Character.isLetterOrDigit(text.charAt(i - 1));
                int after = i + word.length();
                boolean afterOk = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after));
                if (beforeOk && afterOk) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findCloseParen(String text, int openParen) {
        boolean inString = false;
        int depth = 0;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Parênteses sem fechamento no INSERT.");
    }

    /** Faz o parse de uma tupla; end[0] sai após o ')'. */
    private static List<String> parseTuple(String text, int openParen, int[] end) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int depth = 0;
        boolean valueIsString = false;
        boolean valueStarted = false;
        for (int i = openParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\'') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        inString = false;
                    }
                } else {
                    current.append(c);
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
                valueIsString = true;
                valueStarted = true;
                continue;
            }
            if (c == '(') {
                depth++;
                if (depth > 1) {
                    throw new IllegalArgumentException("Expressão dentro da tupla do INSERT não é suportada.");
                }
                continue;
            }
            if (c == ')') {
                depth--;
                values.add(finishValue(current, valueIsString, valueStarted));
                end[0] = i + 1;
                return values;
            }
            if (c == ',' && depth == 1) {
                values.add(finishValue(current, valueIsString, valueStarted));
                current.setLength(0);
                valueIsString = false;
                valueStarted = false;
                continue;
            }
            if (!Character.isWhitespace(c)) {
                valueStarted = true;
            }
            current.append(c);
        }
        throw new IllegalArgumentException("Tupla sem fechamento no INSERT.");
    }

    private static String finishValue(StringBuilder current, boolean isString, boolean started) {
        if (!started) {
            return null;
        }
        String raw = current.toString().trim();
        if (!isString) {
            if (raw.equalsIgnoreCase("NULL")) {
                return null;
            }
            if (raw.equalsIgnoreCase("TRUE")) {
                return "true";
            }
            if (raw.equalsIgnoreCase("FALSE")) {
                return "false";
            }
            if (!raw.matches("-?[0-9]+(\\.[0-9]+)?")) {
                throw new IllegalArgumentException("Expressão no dump não é suportada: " + raw);
            }
        }
        return raw;
    }
}
