package br.com.ziipsan.platform.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * P2.2: trabalho JDBC com contexto de tenant local a transacao, sobre
 * QUALQUER pool por papel.
 *
 * <p>Generaliza, para os consumidores JDBC fora do Hibernate (lote, ingestao
 * e qualquer store novo), o padrao que a P2.1 ja usa dentro do
 * {@code JdbcBatchJobStore} e que a P0.28 usa no
 * {@code MultiTenantConnectionProvider}: a transacao abre, aplica
 * {@code search_path} (schema do tenant + public, onde ficam as extensoes e
 * as funcoes de contexto) e as GUCs {@code app.current_tenant} e
 * {@code app.current_user} com {@code set_config(..., true)} — LOCAL a
 * transacao —, roda o trabalho, faz commit e restaura o autoCommit original
 * antes de devolver a conexao ao pool. No commit/rollback o PostgreSQL
 * descarta o contexto sozinho: a conexao que volta ao pool (Hikari ou
 * PgBouncer em modo transaction) nao carrega o tenant anterior, e a proxima
 * transacao nela, de outro tenant, nao o ve — e o que o IT de isolamento
 * prova nos tres pools, com pool de 1 conexao (reaproveitamento forcado).
 *
 * <p>Sem tenant ({@code tenantId == null}), o contexto aplicado e o vazio:
 * a RLS nao devolve nada e barra INSERT, como o {@code getAnyConnection}
 * do provider. O schema e validado com a mesma regra do provider
 * (minusculas, digitos e {@code _}; {@code public} recusado como schema de
 * tenant, para ninguem operar "no public" por acidente).
 */
public final class TenantTransaction {

    private static final String APPLY_LOCAL = """
            SELECT set_config('search_path', quote_ident(?) || ', public', true),
                   set_config('app.current_tenant', ?, true),
                   set_config('app.current_user', ?, true)""";

    private static final String CLEAR_LOCAL = """
            SELECT set_config('search_path', 'public', true),
                   set_config('app.current_tenant', '', true),
                   set_config('app.current_user', '', true)""";

    private TenantTransaction() {
    }

    /** Trabalho a rodar dentro da transacao com contexto de tenant. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /**
     * Roda o trabalho numa transacao com o contexto do tenant.
     *
     * @param schema   schema do tenant (ou {@code null} com {@code tenantId == null} para sem tenant)
     * @param tenantId id do tenant (ou {@code null} para sem tenant)
     * @param userId   id do usuario executor (ou {@code null} para vazio)
     */
    public static <T> T run(DataSource dataSource, String schema, UUID tenantId, UUID userId,
                            Work<T> work) throws SQLException {
        Objects.requireNonNull(dataSource, "O DataSource e obrigatorio");
        Objects.requireNonNull(work, "O trabalho e obrigatorio");
        if (tenantId == null) {
            if (schema != null) {
                throw new IllegalArgumentException("Schema sem tenant: passe tenantId ou schema null");
            }
        } else {
            validateSchema(schema);
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (tenantId == null) {
                    execute(connection, CLEAR_LOCAL);
                } else {
                    execute(connection, APPLY_LOCAL, schema, tenantId.toString(),
                            userId == null ? "" : userId.toString());
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

    private static void validateSchema(String schema) {
        if (schema == null || !schema.matches("^[a-z][a-z0-9_]{0,62}$") || "public".equals(schema)) {
            throw new IllegalArgumentException("Schema de tenant invalido: " + schema);
        }
    }

    private static void execute(Connection connection, String sql, String... args) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            ps.execute();
        }
    }
}
