package br.com.ziipsan.reporting.query;

import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.spi.ReportUserContext;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executa a consulta montada pelo builder na fonte roteada (replica ou
 *  primario com timeout) e devolve as linhas como mapas ordenados. */
public final class JdbcReportQueryExecutor implements ReportQueryExecutorPort {

    private final ReportQueryBuilder builder = new ReportQueryBuilder();
    private final ReportDataSourceRouter router;

    public JdbcReportQueryExecutor(ReportDataSourceRouter router) {
        this.router = router;
    }

    @Override
    public ExecutionResult execute(ReportDefinition definition, ReportQuery query,
                                   ReportUserContext user, int statementTimeoutMs) throws SQLException {
        ReportQueryBuilder.BuiltQuery built = builder.build(definition, query);
        try (ReportDataSourceRouter.RoutedConnection routed = router.open(statementTimeoutMs);
             PreparedStatement ps = routed.connection().prepareStatement(built.sql())) {
            for (int i = 0; i < built.params().size(); i++) {
                Object param = built.params().get(i);
                if ("__TENANT__".equals(param)) {
                    ps.setObject(i + 1, user.tenantId());
                } else if (param instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setString(i + 1, String.valueOf(param));
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rows.size() >= definition.maxRows()) {
                        truncated = true;
                        break;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (var column : definition.columns()) {
                        row.put(column.name(), rs.getObject(column.name()));
                    }
                    rows.add(row);
                }
            }
            // Sucesso: confirma a transacao aberta pelo router - e ela que
            // da efeito ao SET LOCAL statement_timeout (correcao B4).
            routed.commit();
            return new ExecutionResult(rows, truncated, routed.source());
        }
    }

    public record ExecutionResult(List<Map<String, Object>> rows, boolean truncated,
                                  ReportDataSourceRouter.Source source) {
    }
}
