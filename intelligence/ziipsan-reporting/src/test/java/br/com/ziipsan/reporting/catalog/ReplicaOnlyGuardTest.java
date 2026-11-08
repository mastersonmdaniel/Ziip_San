package br.com.ziipsan.reporting.catalog;

import br.com.ziipsan.reporting.query.ReportDataSourceRouter;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prova da regra da ED-46/P4.3: relatorio do catalogo completo NAO
 * executa no banco primario. Sem replica configurada, a guarda recusa
 * ANTES de qualquer consulta; com conexao sorteada no primario, recusa
 * tambem. Na replica, passa.
 */
class ReplicaOnlyGuardTest {

    @Test
    void fontePrimariaERecusada() {
        assertThatThrownBy(() -> ReplicaOnlyGuard.assertSource(ReportDataSourceRouter.Source.PRIMARY))
                .isInstanceOf(PrimaryAccessForbiddenException.class)
                .hasMessageContaining("primario");
        assertThatCode(() -> ReplicaOnlyGuard.assertSource(ReportDataSourceRouter.Source.READ_REPLICA))
                .doesNotThrowAnyException();
    }

    @Test
    void semReplicaConfiguradaRecusaAntesDeConsultar() {
        ReportDataSourceRouter router = new ReportDataSourceRouter(mock(DataSource.class), null);
        assertThat(router.replicaConfigured()).isFalse();
        assertThatThrownBy(() -> ReplicaOnlyGuard.requireReplicaConfigured(router))
                .isInstanceOf(PrimaryAccessForbiddenException.class)
                .hasMessageContaining("replica");
    }

    @Test
    void conexaoDaReplicaPassaEConexaoDoPrimarioNao() throws Exception {
        ReportDataSourceRouter withReplica = new ReportDataSourceRouter(fakeDataSource(), fakeDataSource());
        try (ReportDataSourceRouter.RoutedConnection connection = withReplica.open(120_000)) {
            assertThat(connection.source()).isEqualTo(ReportDataSourceRouter.Source.READ_REPLICA);
            assertThatCode(() -> ReplicaOnlyGuard.assertReplica(connection)).doesNotThrowAnyException();
            connection.commit();
        }
        ReportDataSourceRouter primaryOnly = new ReportDataSourceRouter(fakeDataSource(), null);
        try (ReportDataSourceRouter.RoutedConnection connection = primaryOnly.open(120_000)) {
            assertThat(connection.source()).isEqualTo(ReportDataSourceRouter.Source.PRIMARY);
            assertThatThrownBy(() -> ReplicaOnlyGuard.assertReplica(connection))
                    .isInstanceOf(PrimaryAccessForbiddenException.class);
            connection.commit();
        }
    }

    /** DataSource falso cujo Connection aceita o SET LOCAL do router (Mockito, como na P3.22). */
    private static DataSource fakeDataSource() throws Exception {
        Statement statement = mock(Statement.class);
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
