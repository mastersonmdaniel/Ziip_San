package br.com.ziipsan.platform.tenant;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
 * app.current_tenant.
 *
 * <p>P0.28 — contexto LOCAL A TRANSACAO (PgBouncer em modo transaction): o provider
 * devolve um proxy da conexao fisica. O contexto so e aplicado no primeiro statement
 * DEPOIS que o Hibernate abre a transacao ({@code setAutoCommit(false)}), com
 * {@code set_config(..., true)}: ao fim da transacao (commit/rollback) o PostgreSQL
 * descarta o contexto sozinho, e duas transacoes de tenants diferentes na mesma
 * conexao fisica nunca se veem — mesmo que o pool ou o PgBouncer reaproveite a
 * conexao sem passar pela devolucao do Hibernate. Na devolucao, o provider ainda
 * limpa explicitamente (escopo de sessao) e fecha, como antes; se a limpeza falhar,
 * a conexao e descartada e nao volta ao pool.
 *
 * <p>Excecao honesta ao "local sempre": uma leitura do Hibernate SEM transacao
 * explicita (sessao aberta so para consulta, sem {@code beginTransaction}) roda em
 * autocommit, e um {@code SET LOCAL} ali morreria no proprio statement que o aplica
 * — a leitura voltaria vazia pela RLS. Para esse caminho, e so para ele, o proxy
 * aplica o contexto em escopo de sessao ({@code false}) no primeiro statement e o
 * limpa na devolucao, como fazia antes da P0.28. O caminho transacional (o unico que
 * o PgBouncer em modo transaction usa, via Spring {@code @Transactional}) e sempre
 * local. Ver {@code TenantConnectionProviderLocalTest} (mock) e o ensaio de vazamento
 * em docs/operacao/local-env.md.
 */
@Slf4j
@Component
public class MultiTenantConnectionProvider
        implements org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider<TenantRef> {

    /** Nome de schema de tenant aceito (minúsculas, dígitos e _; até 63 caracteres). */
    private static final Pattern SCHEMA_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    static final String APPLY_LOCAL = """
            SELECT set_config('search_path', quote_ident(?) || ', public', true),
                   set_config('app.current_tenant', ?, true),
                   set_config('app.current_user', ?, true)""";

    static final String APPLY_SESSION = """
            SELECT set_config('search_path', quote_ident(?) || ', public', false),
                   set_config('app.current_tenant', ?, false),
                   set_config('app.current_user', ?, false)""";

    static final String CLEAR_LOCAL = """
            SELECT set_config('search_path', 'public', true),
                   set_config('app.current_tenant', '', true),
                   set_config('app.current_user', '', true)""";

    static final String CLEAR_SESSION = """
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
        Connection physical = dataSource.getConnection();
        return wrap(physical, null, null, "");
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
        Connection physical = dataSource.getConnection();
        UUID user = TenantContextHolder.getUserId();
        return wrap(physical, tenant.schema(), tenant.tenantId().toString(),
                user == null ? "" : user.toString());
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
        Connection physical = physicalOf(connection);
        try {
            execute(physical, CLEAR_SESSION);
        } catch (SQLException e) {
            log.warn("Falha ao limpar o contexto de tenant da conexão; descartando-a do pool", e);
            discard(physical);
            return;
        }
        physical.close();
    }

    /** Tira a conexão do pool sem devolvê-la: ela pode estar com o contexto de outro tenant. */
    private void discard(Connection connection) {
        Connection physical = physicalOf(connection);
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                dataSource.unwrap(HikariDataSource.class).evictConnection(physical);
                return;
            }
            physical.abort(Runnable::run);
        } catch (SQLException e) {
            log.error("Falha ao descartar conexão com contexto de tenant", e);
        }
    }

    private static Connection physicalOf(Connection connection) {
        if (Proxy.isProxyClass(connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            if (handler instanceof TenantConnectionHandler tenantHandler) {
                return tenantHandler.physical;
            }
        }
        return connection;
    }

    private Connection wrap(Connection physical, String schema, String tenantId, String userId) {
        TenantConnectionHandler handler = new TenantConnectionHandler(physical, schema, tenantId, userId);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    /** Proxy que aplica o contexto do tenant no primeiro statement de cada transacao. */
    static final class TenantConnectionHandler implements InvocationHandler {

        private final Connection physical;
        private final String schema;
        private final String tenantId;
        private final String userId;
        private boolean inTransaction;
        private boolean applied;

        TenantConnectionHandler(Connection physical, String schema, String tenantId, String userId) {
            this.physical = physical;
            this.schema = schema;
            this.tenantId = tenantId;
            this.userId = userId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            switch (name) {
                case "setAutoCommit" -> {
                    boolean autoCommit = (Boolean) args[0];
                    Object result = method.invoke(physical, args);
                    inTransaction = !autoCommit;
                    applied = false;
                    return result;
                }
                case "commit", "rollback" -> {
                    Object result = method.invoke(physical, args);
                    inTransaction = false;
                    applied = false;
                    return result;
                }
                case "createStatement", "prepareStatement", "prepareCall" -> {
                    ensureApplied();
                    return method.invoke(physical, args);
                }
                case "close" -> {
                    return method.invoke(physical, args);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "TenantConnectionProxy[" + (schema == null ? "<sem tenant>" : schema) + "]";
                }
                default -> {
                    return method.invoke(physical, args);
                }
            }
        }

        private void ensureApplied() throws SQLException {
            if (applied) {
                return;
            }
            if (schema == null) {
                execute(physical, inTransaction ? CLEAR_LOCAL : CLEAR_SESSION);
            } else {
                execute(physical, inTransaction ? APPLY_LOCAL : APPLY_SESSION, schema, tenantId, userId);
            }
            applied = true;
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
