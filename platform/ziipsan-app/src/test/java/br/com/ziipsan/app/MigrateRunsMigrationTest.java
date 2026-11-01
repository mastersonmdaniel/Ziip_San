package br.com.ziipsan.app;

import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.6: o perfil {@code migrate} executa a migração.
 *
 * <p>Prova em duas partes: (1) os três beans do migrador existem (dois DataSources, o
 * de migração com o usuário migrador) e o contexto sobe com
 * {@code ziipsan.migrate.exit-after=false} — sem essa flag, o MigrateExitRunner
 * mataria a JVM do Surefire com {@code System.exit}; (2) o serviço de provisionamento
 * do perfil migrate provisiona um tenant novo de verdade (schema + migrations +
 * catálogo ACTIVE), o que só funciona com o DataSource de migração.
 *
 * <p>O tenant de prova é removido ao fim do teste (schema + linha do catálogo), para
 * não vazar para os demais testes de integração, que compartilham o banco descartável.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("migrate")
// P0.14: sem o registro BFF explicito, o auto-config do Boot tentaria montar o
// repositorio a partir das properties — e o issuer-uri vazio (override de teste
// para evitar discovery) explode com "issuer cannot be empty". Ver BffTestClientConfig.
@org.springframework.context.annotation.Import(BffTestClientConfig.class)
class MigrateRunsMigrationTest extends MigrationProfileTestBase {

    @DynamicPropertySource
    static void migrateProperties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("ziipsan.migrator.user", () -> ProvisionedTenant.migratorUser);
        registry.add("ziipsan.migrator.password", () -> ProvisionedTenant.migratorPassword);
        registry.add("ziipsan.db.migrate-on-startup", () -> "true");
        // Sem isso o runner do job sairia com System.exit e mataria o fork do Surefire.
        registry.add("ziipsan.migrate.exit-after", () -> "false");
        registry.add("ziipsan.crypto.provider", () -> "dev-env");
        registry.add("ziipsan.crypto.dev-master-key", MigrateRunsMigrationTest::randomKey);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier("migratorDataSource")
    private HikariDataSource migratorDataSource;

    @Autowired
    private TenantProvisioningService provisioning;

    @Test
    @DisplayName("migrate: beans do migrador existem e o provisionamento cria schema, tabelas e catálogo")
    void migrateProvisionsTenant() {
        assertThat(context.getBeanNamesForType(HikariDataSource.class)).hasSize(2);
        assertThat(context.containsBean("migratorDataSource")).isTrue();
        assertThat(context.containsBean("databaseMigrator")).isTrue();
        assertThat(context.containsBean("tenantProvisioningService")).isTrue();
        assertThat(migratorDataSource.getUsername()).isEqualTo(ProvisionedTenant.migratorUser);

        // Core migrado pelo DatabaseMigrator na subida: o catálogo existe e tem o tenant
        // de integração (provisionado por ProvisionedTenant antes do contexto subir).
        JdbcTemplate migrator = new JdbcTemplate(migratorDataSource);
        Integer catalogRows = migrator.queryForObject(
                "SELECT count(*) FROM public.tenant_catalog WHERE schema_name = ?",
                Integer.class, ProvisionedTenant.SCHEMA);
        assertThat(catalogRows).isOne();

        // Provisionamento novo pelo bean do perfil migrate (não construção manual).
        String schema = "it_mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try {
            var result = provisioning.provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                    .tenantId(UUID.randomUUID())
                    .schemaName(schema)
                    .displayName("Tenant de teste do perfil migrate")
                    .email("migrate@teste.invalid")
                    .plan("PRO")
                    .build());
            assertThat(result.success())
                    .as("o provisionamento pelo bean do perfil migrate deve concluir: %s", result.error())
                    .isTrue();
            Integer tables = migrator.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = ?",
                    Integer.class, schema);
            assertThat(tables).isGreaterThan(0);
            String status = migrator.queryForObject(
                    "SELECT status FROM public.tenant_catalog WHERE schema_name = ?",
                    String.class, schema);
            assertThat(status).isEqualTo("ACTIVE");
        } finally {
            migrator.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            migrator.update("DELETE FROM public.tenant_catalog WHERE schema_name = ?", schema);
        }
    }
}
