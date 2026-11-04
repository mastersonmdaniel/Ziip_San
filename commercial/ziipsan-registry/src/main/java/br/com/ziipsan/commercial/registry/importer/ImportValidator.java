package br.com.ziipsan.commercial.registry.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P1.40: validação por linha do importador genérico — pura, sem banco, exceto
 * pelas duas checagens que exigem o banco (chave estrangeira e duplicidade no
 * banco), entregues pela interface {@link DbChecks}. Uma linha é aceita só se
 * não tiver nenhuma recusa; cada recusa vira uma linha do relatório (linha,
 * campo, motivo), e a mensagem nunca ecoa o valor recusado.
 *
 * <p>Motivos (estáveis, usados no CSV de recusas e no teste): REQUIRED,
 * INVALID_TYPE, INVALID_DOMAIN, FOREIGN_KEY_NOT_FOUND, DUPLICATE_IN_FILE,
 * DUPLICATE_IN_DATABASE. Booleano aceita {@code true/false} e {@code
 * sim/não} (o modelo é brasileiro); data aceita só ISO ({@code AAAA-MM-DD});
 * número aceita ponto decimal, nunca vírgula (a vírgula é o separador).
 */
public final class ImportValidator {

    private ImportValidator() {
    }

    /** Checagens que exigem o banco do tenant. Implementação JDBC no serviço. */
    public interface DbChecks {
        /** Existe a linha referenciada pela FK (tabela, coluna, valor) no tenant? */
        boolean foreignKeyExists(String table, String column, String value);

        /** A linha já existe no banco por este esquema (chave do aplicador)? */
        boolean existsInDatabase(Map<String, String> values);
    }

    /** Uma recusa: linha, campo, motivo e mensagem (sem o valor recusado). */
    public record Rejection(int rowNumber, String field, String reasonCode, String message) {
    }

    /** Veredicto da validação: linhas aceitas e recusas, na ordem do arquivo. */
    public record ValidationResult(List<ImportCsvParser.ParsedRow> accepted, List<Rejection> rejections) {
    }

    public static ValidationResult validate(ImportSchema schema, ImportCsvParser.ParsedCsv csv, DbChecks db) {
        List<ImportColumn> columns = schema.columns();
        List<Rejection> rejections = new ArrayList<>();
        List<ImportCsvParser.ParsedRow> accepted = new ArrayList<>();
        // Duplicidade no arquivo: só nas colunas-chave, pela chave composta.
        List<ImportColumn> keyColumns = columns.stream().filter(ImportColumn::uniqueKey).toList();
        Set<String> seenKeys = new HashSet<>();
        Map<String, Integer> firstRowByKey = new HashMap<>();

        for (ImportCsvParser.ParsedRow row : csv.rows()) {
            List<Rejection> rowRejections = new ArrayList<>();
            for (ImportColumn column : columns) {
                String value = row.values().getOrDefault(column.name(), "");
                if (value.isBlank()) {
                    if (column.required()) {
                        rowRejections.add(new Rejection(row.number(), column.name(), "REQUIRED",
                                "Campo obrigatorio nao preenchido."));
                    }
                    continue;
                }
                if (!validType(column.type(), value)) {
                    rowRejections.add(new Rejection(row.number(), column.name(), "INVALID_TYPE",
                            "Formato invalido para o tipo " + column.type() + "."));
                    continue;
                }
                if (!column.domain().isEmpty() && !column.domain().contains(value)) {
                    rowRejections.add(new Rejection(row.number(), column.name(), "INVALID_DOMAIN",
                            "Valor fora do dominio permitido para o campo."));
                    continue;
                }
                if (column.foreignKey() != null
                        && !db.foreignKeyExists(column.foreignKey().table(), column.foreignKey().column(), value)) {
                    rowRejections.add(new Rejection(row.number(), column.name(), "FOREIGN_KEY_NOT_FOUND",
                            "Referencia nao encontrada no cadastro."));
                }
            }
            if (!keyColumns.isEmpty()) {
                boolean allBlank = keyColumns.stream()
                        .allMatch(c -> row.values().getOrDefault(c.name(), "").isBlank());
                String key = keyOf(row, keyColumns);
                if (!allBlank) {
                    if (!seenKeys.add(key)) {
                        rowRejections.add(new Rejection(row.number(), keyColumns.get(0).name(),
                                "DUPLICATE_IN_FILE",
                                "Linha duplicada no arquivo (primeira ocorrencia na linha "
                                        + firstRowByKey.get(key) + ")."));
                    } else {
                        firstRowByKey.put(key, row.number());
                    }
                }
            }
            if (rowRejections.isEmpty() && db.existsInDatabase(row.values())) {
                rowRejections.add(new Rejection(row.number(), keyColumns.isEmpty()
                        ? columns.get(0).name() : keyColumns.get(0).name(),
                        "DUPLICATE_IN_DATABASE", "Registro ja existe no cadastro."));
            }
            if (rowRejections.isEmpty()) {
                accepted.add(row);
            } else {
                rejections.addAll(rowRejections);
            }
        }
        return new ValidationResult(List.copyOf(accepted), List.copyOf(rejections));
    }

    private static String keyOf(ImportCsvParser.ParsedRow row, List<ImportColumn> keyColumns) {
        StringBuilder key = new StringBuilder();
        for (ImportColumn column : keyColumns) {
            key.append('\u0001').append(row.values().getOrDefault(column.name(), "").strip());
        }
        return key.toString();
    }

    static boolean validType(ImportColumn.ColumnType type, String value) {
        try {
            switch (type) {
                case TEXT -> {
                    return true;
                }
                case INTEGER -> {
                    Integer.parseInt(value);
                    return true;
                }
                case DECIMAL -> {
                    new BigDecimal(value);
                    return true;
                }
                case BOOLEAN -> {
                    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                            || value.equalsIgnoreCase("sim") || value.equalsIgnoreCase("nao")
                            || value.equalsIgnoreCase("não");
                }
                case DATE -> {
                    LocalDate.parse(value);
                    return true;
                }
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            return false;
        }
        return false;
    }

    /** Converte o texto da planilha para o booleano do banco (sim/não incluídos). */
    public static boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("sim");
    }
}
