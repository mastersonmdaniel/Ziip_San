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
 * P0.42: o job {@code migrate} grava a implantação em {@code platform.deployment}.
 *
 * <p>O contexto sobe com o perfil {@code migrate} e {@code migrate-on-startup=true},
 * então o {@code DatabaseMigrator} já rodou antes do teste (como em
 * {@code MigrateRunsMigrationTest}). Sem build-info/git.properties no classpath de
 * teste, versão e ambiente caem nos fallbacks literais ({@code "dev"}); o que se
 * prova aqui é a GRAVAÇÃO — uma linha nova por execução do job — e não um valor
 * de artefato que o teste não tem. Linhas acumuladas no banco descartável de
 * integração são esperadas (uma por boot do perfil migrate); os demais testes
 * usam "mais recente" ou "contém", nunca contagem exata da tabela.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("migrate")
@org.springframework.context.annotation.Import(BffTestClientConfig.class)
class DeploymentRecordedByMigrateTest extends MigrationProfileTestBase {

    @DynamicPropertySource
    static void migrateProperties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("ziipsan.migrator.user", () -> ProvisionedTenant.migratorUser);
        registry.add("ziipsan.migrator.password", () -> ProvisionedTenant.migratorPassword);
        registry.add("ziipsan.db.migrate-on-startup", () -> "true");
        registry.add("ziipsan.migrate.exit-after", () -> "false");
        registry.add("ziipsan.crypto.provider", () -> "dev-env");
        registry.add("ziipsan.crypto.dev-master-key", DeploymentRecordedByMigrateTest::randomKey);
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
    @DisplayName("migrate grava platform.deployment com versão, ambiente, início e fim")
    void migrateRecordsDeployment() {
        JdbcTemplate migrator = new JdbcTemplate(migratorDataSource);
        Integer schema = migrator.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'platform'", Integer.class);
        assertThat(schema).isOne();
        Integer rows = migrator.queryForObject(
                "SELECT count(*) FROM platform.deployment WHERE version = 'dev' AND environment = 'dev'",
                Integer.class);
        assertThat(rows).as("ao menos uma implantação gravada pelo job migrate").isGreaterThanOrEqualTo(1);
        Integer complete = migrator.queryForObject(
                "SELECT count(*) FROM platform.deployment"
                        + " WHERE version = 'dev' AND environment = 'dev'"
                        + " AND started_at IS NOT NULL AND finished_at IS NOT NULL AND created_at IS NOT NULL",
                Integer.class);
        assertThat(complete).isGreaterThanOrEqualTo(1);
    }
}
