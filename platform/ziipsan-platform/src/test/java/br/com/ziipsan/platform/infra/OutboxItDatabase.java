package br.com.ziipsan.platform.infra;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Banco de integração da frente P1.9: instance dedicada e virgem (porta
 * 55435, security.sql do worktree, ver o roteiro da frente), migrations de
 * core aplicadas uma única vez por JVM via {@link CoreMigrationRunner}
 * (como ProvisionedTenant faz no módulo app).
 *
 * <p>Credenciais por ambiente, sem valor padrão versionado (mesmo padrão
 * do ProvisionedTenant): a senha do worker vem de
 * {@code ZIIPSAN_IT_WORKER_PASSWORD}, que o roteiro da frente gera ao dar
 * LOGIN à role ziipsan_worker (NOLOGIN no security.sql). Sem ela, os testes
 * falham rápido com o nome da variável — nunca com banco inventado.
 */
final class OutboxItDatabase {

    static String url;
    static DataSource appDs;
    static DataSource workerDs;
    static DataSource migratorDs;

    private static boolean ready;

    private OutboxItDatabase() {
    }

    static synchronized void ensure() {
        if (ready) {
            return;
        }
        url = env("ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://127.0.0.1:55435/ziipsan");
        appDs = new DriverManagerDataSource(url,
                env("ZIIPSAN_IT_DB_USER", "ziipsan_app"), requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        migratorDs = new DriverManagerDataSource(url,
                env("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));
        workerDs = new DriverManagerDataSource(url, "ziipsan_worker",
                requireEnv("ZIIPSAN_IT_WORKER_PASSWORD"));
        new CoreMigrationRunner(migratorDs).run(null);
        ready = true;
    }

    static Connection appConnection() throws SQLException {
        ensure();
        return appDs.getConnection();
    }

    static Connection workerConnection() throws SQLException {
        ensure();
        return workerDs.getConnection();
    }

    static void setTenantSession(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery().close();
        }
    }

    static int countById(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM public.outbox WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static String statusOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM public.outbox WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    static int attemptsOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT attempts FROM public.outbox WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static String lastErrorOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_error FROM public.outbox WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    static String availableAtOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT available_at::text FROM public.outbox WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    static String queueName() {
        return "it-p19-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("variavel de integracao ausente: " + name);
        }
        return value;
    }
}
