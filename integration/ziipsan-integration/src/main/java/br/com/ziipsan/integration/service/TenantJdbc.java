package br.com.ziipsan.integration.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Transação no schema do tenant (mesmo padrão do {@code GlobalSearchService},
 * P1.33): {@code SET LOCAL search_path} + {@code app.current_tenant} para a
 * RLS forçada, filtro explícito por {@code tenant_id} em toda consulta,
 * commit/rollback explícitos. Sem isto, uma consulta sem filtro veria
 * outros tenants onde a RLS não alcançasse — a defesa é em duas camadas.
 */
public final class TenantJdbc {

    private TenantJdbc() {
    }

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    public static <T> T inTenantTransaction(DataSource dataSource, Work<T> work) {
        String schema = IntegrationContext.schema();
        java.util.UUID tenantId = IntegrationContext.tenantId();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL search_path TO " + schema + ", public");
                }
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    setTenant.setString(1, tenantId.toString());
                    setTenant.executeQuery();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // O erro original é o que importa.
                }
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de banco no schema do tenant", e);
        }
    }
}
