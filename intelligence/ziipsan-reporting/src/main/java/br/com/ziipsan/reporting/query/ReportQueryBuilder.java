package br.com.ziipsan.reporting.query;

import br.com.ziipsan.reporting.registry.ReportDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta o SQL da execucao SOMENTE com identificadores declarados na
 * definicao (view, colunas, filtros, ordenacao, agrupamento). Valores de
 * filtro viram parametros {@code ?} - nenhum texto do usuario entra no SQL.
 * Tentativa de usar coluna/filtro/ordenacao fora da declaracao e recusada.
 */
public final class ReportQueryBuilder {

    public BuiltQuery build(ReportDefinition definition, ReportQuery query) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selectColumns = new ArrayList<>();
        if (query.groupBy().isEmpty()) {
            definition.columns().forEach(c -> selectColumns.add(c.name()));
        } else {
            for (String group : query.groupBy()) {
                definition.column(group);
                selectColumns.add(group);
            }
            definition.columns().stream()
                    .filter(c -> !query.groupBy().contains(c.name()))
                    .forEach(c -> selectColumns.add(aggregation(c)));
        }
        sql.append(String.join(", ", selectColumns));
        sql.append(" FROM ").append(definition.viewName());
        sql.append(" WHERE tenant_id = ?");
        params.add("__TENANT__");
        for (var entry : query.filters().entrySet()) {
            String column = entry.getKey();
            if (!definition.filterable(column)) {
                throw new IllegalArgumentException(
                        "Filtro nao declarado em " + definition.code() + ": " + column);
            }
            ReportQuery.FilterValue value = entry.getValue();
            if (value.single() != null) {
                sql.append(" AND ").append(column).append(" = ?");
                params.add(value.single());
            } else {
                if (value.from() != null) {
                    sql.append(" AND ").append(column).append(" >= ?");
                    params.add(value.from());
                }
                if (value.to() != null) {
                    sql.append(" AND ").append(column).append(" <= ?");
                    params.add(value.to());
                }
            }
        }
        if (!query.groupBy().isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", query.groupBy()));
        }
        String sort = query.sortColumn() != null ? query.sortColumn() : definition.defaultSortColumn();
        if (sort != null) {
            definition.column(sort);
            sql.append(" ORDER BY ").append(sort).append(query.ascending() ? " ASC" : " DESC");
        }
        sql.append(" LIMIT ?");
        params.add(definition.maxRows() + 1);
        return new BuiltQuery(sql.toString(), List.copyOf(params));
    }

    /**
     * Agregacao de agrupamento (correcao R2): so coluna declarada
     * {@code aggregable} vira SUM. Coluna nao agregavel (razao/percentual
     * DECIMAL, texto, data) vira MAX - valor representativo do grupo,
     * nunca uma soma sem significado (somar percentual_nao_atendido de
     * dois grupos nao e o percentual de nenhum deles).
     */
    private static String aggregation(br.com.ziipsan.reporting.registry.ReportColumn column) {
        return column.aggregable()
                ? "SUM(" + column.name() + ") AS " + column.name()
                : "MAX(" + column.name() + ") AS " + column.name();
    }

    public record BuiltQuery(String sql, List<Object> params) {
    }
}
