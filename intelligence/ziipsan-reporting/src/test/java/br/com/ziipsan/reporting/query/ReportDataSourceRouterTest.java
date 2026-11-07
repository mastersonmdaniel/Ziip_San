package br.com.ziipsan.reporting.query;

import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Correcao B4: o SET LOCAL statement_timeout so tem efeito DENTRO de
 * transacao. A prova e a ORDEM das chamadas sobre a conexao: auto-commit
 * desligado ANTES do SET LOCAL, consulta na mesma transacao, commit ao
 * fim e auto-commit original restaurado na devolucao.
 */
class ReportDataSourceRouterTest {

    @Test
    void timeoutAplicadoDentroDeTransacaoExplicita() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        when(prepared.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        ReportDataSourceRouter router = new ReportDataSourceRouter(dataSource, null);
        JdbcReportQueryExecutor executor = new JdbcReportQueryExecutor(router);
        var definition = new ReportRegistry().find("RPT-001").orElseThrow();
        var user = new ReportUserContext(UUID.randomUUID(), UUID.randomUUID(),
                Set.of("reporting:export"), ReportUserContext.MaskCeiling.FULL, Set.of());
        var result = executor.execute(definition, ReportQuery.empty(), user, 120_000);

        assertThat(result.rows()).isEmpty();
        assertThat(result.source()).isEqualTo(ReportDataSourceRouter.Source.PRIMARY);
        InOrder order = inOrder(connection, statement, prepared);
        order.verify(connection).setAutoCommit(false);
        order.verify(statement).execute("SET LOCAL statement_timeout = 120000");
        order.verify(prepared).executeQuery();
        order.verify(connection).commit();
        // Devolucao restaura o estado original da conexao antes de fechar.
        verify(connection).setAutoCommit(true);
        verify(connection).close();
    }
}
