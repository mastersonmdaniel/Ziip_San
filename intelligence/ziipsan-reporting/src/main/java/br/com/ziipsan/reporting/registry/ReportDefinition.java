package br.com.ziipsan.reporting.registry;

import java.util.List;

/**
 * Definicao declarativa de relatorio (P3.22): view-fonte, colunas, filtros,
 * ordenacao padrao, permissao de exportacao e teto de linhas. O nucleo so
 * executa o que esta declarado aqui - identificador fora da declaracao e
 * recusado pelo ReportQueryBuilder (nunca vira SQL).
 */
public record ReportDefinition(
        String code,
        String title,
        ReportOrigin origin,
        String viewName,
        String requiredPermission,
        List<ReportColumn> columns,
        List<ReportFilter> filters,
        String defaultSortColumn,
        int maxRows) {

    /** Teto padrao de linhas por execucao (a regra da P1.12 limita e desvia
     *  o excedente para a entrega assincrona em Arquivos gerados). */
    public static final int DEFAULT_MAX_ROWS = 10_000;

    /** Limite sincrono da P1.12: acima dele, geracao assincrona pelo worker. */
    public static final int SYNC_ROW_LIMIT = 1_000;

    public ReportDefinition {
        if (code == null || !code.matches("[A-Z]{2,4}-[0-9]{3}|[A-Z][A-Z0-9_\\-]{2,59}")) {
            throw new IllegalArgumentException("Codigo de relatorio invalido: " + code);
        }
        if (viewName == null || !viewName.matches("rpt_[a-z0-9_]+")) {
            throw new IllegalArgumentException("View fora do padrao rpt_*: " + viewName);
        }
        // Correcao G5 pos-revisao: nao basta o sufixo :export. A permissao
        // tem de ser <prefixo>.<codigo-em-minusculas>:export - o mesmo
        // padrao que o ReportUserContext.canExport confere (ex.:
        // reporting.rpt-001:export; certification.cert-001:export).
        // Permissao generica de outro recurso nao registra relatorio.
        if (requiredPermission == null
                || !requiredPermission.matches("[a-z][a-z0-9_-]*\\."
                        + java.util.regex.Pattern.quote(code.toLowerCase()) + ":export")) {
            throw new IllegalArgumentException(
                    "Permissao de relatorio deve ser <prefixo>.<codigo>:export (P1.12): "
                            + requiredPermission);
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Relatorio sem colunas: " + code);
        }
        columns = List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        if (maxRows <= 0) {
            maxRows = DEFAULT_MAX_ROWS;
        }
        if (defaultSortColumn != null && columns.stream().noneMatch(c -> c.name().equals(defaultSortColumn))) {
            throw new IllegalArgumentException(
                    "Ordenacao padrao fora das colunas declaradas: " + defaultSortColumn);
        }
    }

    public ReportColumn column(String name) {
        return columns.stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Coluna nao declarada em " + code + ": " + name));
    }

    public boolean hasColumn(String name) {
        return columns.stream().anyMatch(c -> c.name().equals(name));
    }

    public boolean filterable(String name) {
        return filters.stream().anyMatch(f -> f.column().equals(name));
    }
}
