package br.com.ziipsan.reporting.query;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializacao do {@link ReportQuery} para a coluna {@code params}
 * (jsonb) de agendamentos e execucoes - e a reconstrucao na execucao
 * (correcao G1 pos-revisao: o worker agendado executava
 * {@code ReportQuery.empty()} e ignorava os params gravados).
 *
 * <p>Formato: {@code {"filters": {"<coluna>": {"single": "..."} ou
 * {"from": "...", "to": "..."}}, "groupBy": [...], "sortColumn": "...",
 * "ascending": true}}. A leitura tolera chaves ausentes (params antigos
 * so com filters/groupBy) e JSON nulo/em branco (query vazia); JSON
 * invalido lanca excecao pela classe {@code Json} do core - nunca vira
 * query vazia em silencio.
 */
public final class ReportQueryJson {

    private ReportQueryJson() {
    }

    public static String toJson(ReportQuery query) {
        Map<String, Object> filters = new LinkedHashMap<>();
        query.filters().forEach((column, value) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (value.single() != null) {
                entry.put("single", value.single());
            }
            if (value.from() != null) {
                entry.put("from", value.from());
            }
            if (value.to() != null) {
                entry.put("to", value.to());
            }
            filters.put(column, entry);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("filters", filters);
        root.put("groupBy", query.groupBy());
        if (query.sortColumn() != null) {
            root.put("sortColumn", query.sortColumn());
        }
        root.put("ascending", query.ascending());
        return Json.write(root);
    }

    public static ReportQuery fromJson(String json) {
        if (json == null || json.isBlank()) {
            return ReportQuery.empty();
        }
        Map<String, Object> root = Json.readMap(json);
        Map<String, ReportQuery.FilterValue> filters = new LinkedHashMap<>();
        if (root.get("filters") instanceof Map<?, ?> filterMap) {
            filterMap.forEach((column, raw) -> {
                if (raw instanceof Map<?, ?> value) {
                    String single = asString(value.get("single"));
                    String from = asString(value.get("from"));
                    String to = asString(value.get("to"));
                    filters.put(String.valueOf(column),
                            single != null ? ReportQuery.FilterValue.eq(single)
                                    : ReportQuery.FilterValue.range(from, to));
                }
            });
        }
        List<String> groupBy = root.get("groupBy") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        String sortColumn = asString(root.get("sortColumn"));
        boolean ascending = !(root.get("ascending") instanceof Boolean flag) || flag;
        return new ReportQuery(filters, sortColumn, ascending, groupBy);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
