package br.com.ziipsan.app;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.14 no caminho real do job migrate: com o perfil {@code migrate} no ar, o
 * {@code DatabaseMigrator} já rodou na subida (ver MigrateRunsMigrationTest) e
 * o tenant de integração — já atualizado, sem migration pendente — deve sair
 * com situação SUCCEEDED e versão registrada no catálogo. É a prova de que a
 * troca do laço sequencial pelo rollout não perdeu a atualização de
 * {@code tenant_schema_version} da P0.21.
 *
 * <p>Sem System.exit: {@code ziipsan.migrate.exit-after=false}, como no teste
 * do perfil migrate.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("migrate")
@org.springframework.context.annotation.Import(BffTestClientConfig.class)
class DatabaseMigratorRolloutStatusTest extends MigrationProfileTestBase {

    @DynamicPropertySource
    static void migrateProperties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("ziipsan.migrator.user", () -> ProvisionedTenant.migratorUser);
        registry.add("ziipsan.migrator.password", () -> ProvisionedTenant.migratorPassword);
        registry.add("ziipsan.db.migrate-on-startup", () -> "true");
        registry.add("ziipsan.migrate.exit-after", () -> "false");
        registry.add("ziipsan.crypto.provider", () -> "dev-env");
        registry.add("ziipsan.crypto.dev-master-key", DatabaseMigratorRolloutStatusTest::randomKey);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    @Qualifier("migratorDataSource")
    private HikariDataSource migratorDataSource;

    @Test
    @DisplayName("migrate: tenant atualizado sai SUCCEEDED com tenant_schema_version gravada")
    void migratedTenantIsSucceededWithVersion() {
        JdbcTemplate migrator = new JdbcTemplate(migratorDataSource);
        String status = migrator.queryForObject(
                "SELECT migration_status FROM public.tenant_catalog WHERE schema_name = ?",
                String.class, ProvisionedTenant.SCHEMA);
        String version = migrator.queryForObject(
                "SELECT tenant_schema_version FROM public.tenant_catalog WHERE schema_name = ?",
                String.class, ProvisionedTenant.SCHEMA);
        String error = migrator.queryForObject(
                "SELECT migration_error FROM public.tenant_catalog WHERE schema_name = ?",
                String.class, ProvisionedTenant.SCHEMA);
        assertThat(status).isEqualTo("SUCCEEDED");
        assertThat(version).as("versão Flyway de tenant aplicada").isNotBlank();
        assertThat(error).isNull();
    }
}
