package br.com.ziipsan.reporting.query;

import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.spi.ReportUserContext;

import java.sql.SQLException;

/** Porta de execucao de consulta (a implementacao real e JDBC na replica/primario). */
public interface ReportQueryExecutorPort {

    JdbcReportQueryExecutor.ExecutionResult execute(ReportDefinition definition, ReportQuery query,
                                                    ReportUserContext user, int statementTimeoutMs)
            throws SQLException;
}
