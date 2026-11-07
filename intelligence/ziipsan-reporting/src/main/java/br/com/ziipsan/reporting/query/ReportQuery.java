package br.com.ziipsan.reporting.query;

import java.util.List;
import java.util.Map;

/**
 * Pedido de execucao: valores de filtro (sempre parametros, nunca SQL),
 * ordenacao e agrupamento pedidos - todos validados contra a definicao.
 */
public record ReportQuery(Map<String, FilterValue> filters, String sortColumn,
                          boolean ascending, List<String> groupBy) {

    public ReportQuery {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
    }

    public static ReportQuery empty() {
        return new ReportQuery(Map.of(), null, true, List.of());
    }

    /** Valor de filtro: igualdade (single) ou faixa (from/to). */
    public record FilterValue(String single, String from, String to) {
        public static FilterValue eq(String value) {
            return new FilterValue(value, null, null);
        }

        public static FilterValue range(String from, String to) {
            return new FilterValue(null, from, to);
        }
    }
}
