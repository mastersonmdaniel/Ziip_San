package br.com.ziipsan.platform.batch;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Banco de integração da P2.1: mesmo padrão do OutboxItDatabase da
 * P1.9. As migrations de core (incluindo a V10) rodam uma única vez
 * por JVM via {@link CoreMigrationRunner}, como o ProvisionedTenant
 * faz no módulo app.
 *
 * <p>Credenciais por ambiente, sem valor padrão versionado: a senha do
 * worker vem de {@code ZIIPSAN_IT_WORKER_PASSWORD} (a role
 * ziipsan_worker recebe LOGIN no roteiro de IT, como no CI). Sem ela,
 * os testes falham rápido com o nome da variável — nunca com banco
 * inventado. O store e o executor do Batch rodam sobre o DataSource
 * do worker, a role que opera lotes de todos os tenants.
 */
final class BatchItDatabase {

    static String url;
    static DataSource workerDs;
    static DataSource migratorDs;

    private static boolean ready;

    private BatchItDatabase() {
    }

    static synchronized void ensure() {
        if (ready) {
            return;
        }
        url = env("ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://127.0.0.1:55433/ziipsan");
        workerDs = new DriverManagerDataSource(url, "ziipsan_worker",
                requireEnv("ZIIPSAN_IT_WORKER_PASSWORD"));
        migratorDs = new DriverManagerDataSource(url,
                env("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));
        new CoreMigrationRunner(migratorDs).run(null);
        ready = true;
    }

    static void setTenantSession(Connection connection, UUID tenantId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery().close();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Variável de ambiente obrigatória para o IT ausente: " + name);
        }
        return value;
    }
}
