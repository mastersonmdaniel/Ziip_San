package br.com.sigsan.platform.tenant;

import br.com.sigsan.core.tenant.TenantContextHolder;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Conexões por tenant para o Hibernate.
 *
 * <p>Ao entregar a conexão define, numa única ida ao banco, o search_path (schema do tenant +
 * public, onde ficam as extensões) e as GUCs app.current_tenant e app.current_user. A RLS das
 * tabelas de tenant e de public.audit_log compara tenant_id com current_tenant_id(), que lê
 * app.current_tenant. Antes este provider só trocava o schema e nada definia a GUC: com RLS FORCE,
 * toda leitura via JPA voltaria vazia e toda gravação seria barrada (achado C2 do PDCA de
 * 12/10/2026). Os testes passavam porque definiam a GUC à mão.
 *
 * <p>Escopo de sessão ({@code set_config(..., false)}), não de transação: o Hibernate obtém a
 * conexão antes do BEGIN. Por isso o estado é SEMPRE sobrescrito na entrega e SEMPRE limpo na
 * devolução; se a limpeza falhar, a conexão é descartada e não volta ao pool, para que o próximo
 * uso não herde o tenant anterior.
 */
@Slf4j
@Component
public class MultiTenantConnectionProvider
        implements org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider<TenantRef> {

    /** Nome de schema de tenant aceito (minúsculas, dígitos e _; até 63 caracteres). */
    private static final Pattern SCHEMA_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private static final String APPLY_TENANT = """
            SELECT set_config('search_path', quote_ident(?) || ', public', false),
                   set_config('app.current_tenant', ?, false),
                   set_config('app.current_user', ?, false)""";

    private static final String CLEAR_TENANT = """
            SELECT set_config('search_path', 'public', false),
                   set_config('app.current_tenant', '', false),
                   set_config('app.current_user', '', false)""";

    private final DataSource dataSource;

    public MultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Conexão sem tenant: a RLS não devolve nada e barra INSERT. */
    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            execute(connection, CLEAR_TENANT);
        } catch (SQLException e) {
            discard(connection);
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        release(connection);
    }

    @Override
    public Connection getConnection(TenantRef tenant) throws SQLException {
        if (tenant == null || tenant.isNone()) {
            return getAnyConnection();
        }
        if (!SCHEMA_NAME.matcher(tenant.schema()).matches() || "public".equals(tenant.schema())) {
            throw new HibernateException("Schema de tenant inválido: " + tenant.schema());
        }
        Connection connection = dataSource.getConnection();
        try {
            UUID user = TenantContextHolder.getUserId();
            execute(connection, APPLY_TENANT,
                    tenant.schema(), tenant.tenantId().toString(), user == null ? "" : user.toString());
        } catch (SQLException e) {
            discard(connection);
            throw new HibernateException("Não foi possível aplicar o contexto do tenant " + tenant.schema(), e);
        }
        return connection;
    }

    @Override
    public void releaseConnection(TenantRef tenant, Connection connection) throws SQLException {
        release(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return true;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (unwrapType.isInstance(this)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Não é possível desembrulhar como " + unwrapType.getName());
    }

    private void release(Connection connection) throws SQLException {
        try {
            execute(connection, CLEAR_TENANT);
        } catch (SQLException e) {
            log.warn("Falha ao limpar o contexto de tenant da conexão; descartando-a do pool", e);
            discard(connection);
            return;
        }
        connection.close();
    }

    /** Tira a conexão do pool sem devolvê-la: ela pode estar com o contexto de outro tenant. */
    private void discard(Connection connection) {
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                dataSource.unwrap(HikariDataSource.class).evictConnection(connection);
                return;
            }
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.error("Falha ao descartar conexão com contexto de tenant", e);
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
