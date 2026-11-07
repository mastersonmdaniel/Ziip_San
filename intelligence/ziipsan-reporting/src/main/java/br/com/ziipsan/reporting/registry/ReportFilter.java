package br.com.ziipsan.reporting.registry;

/** Filtro declarativo: so colunas declaradas podem ser filtradas (whitelist). */
public record ReportFilter(String column, Operator operator) {

    public enum Operator {
        /** Igualdade para texto; faixa [de, ate] para data/numero. */
        EQ_OR_RANGE
    }

    public ReportFilter {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Filtro sem coluna");
        }
        if (operator == null) {
            operator = Operator.EQ_OR_RANGE;
        }
    }
}
