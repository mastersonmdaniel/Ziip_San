package br.com.ziipsan.platform.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Executa trabalho JDBC com o contexto de tenant da sessão (RLS).
 *
 * <p>A policy de audit_log é FORCED: sem {@code app.current_tenant} setado,
 * nem o dono da tabela enxerga as linhas. O contexto é local à transação
 * (morre no commit/rollback), e o autoCommit original é restaurado antes de
 * devolver a conexão ao pool — conexão suja no pool vazaria tenant para a
 * próxima requisição (o mesmo motivo da V1 ter removido o set de sessão).
 */
final class TenantScopedConnection {

    private TenantScopedConnection() {
    }

    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    static <T> T run(DataSource dataSource, UUID tenantId, Work<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }
}
