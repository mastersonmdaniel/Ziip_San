package br.com.sigsan.app;

import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.migration.CoreMigrationRunner;
import br.com.sigsan.platform.tenant.KeycloakAdminService;
import br.com.sigsan.platform.tenant.TenantProvisioningService;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Banco virgem + migration de core + um tenant provisionado com todos os módulos, uma única vez por
 * JVM de teste. As classes de teste de integração compartilham este tenant e criam os próprios dados
 * dentro dele.
 *
 * <p>O schema do tenant é descartado ao fim da JVM. O banco precisa ser virgem (ver
 * {@code scripts/reset-it-db.sh}): a migration de core roda sobre {@code public}.
 */
final class ProvisionedTenant {

    static final UUID TENANT_ID = UUID.randomUUID();
    static final String SCHEMA = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    static String url;
    static String appUser;
    static String appPassword;
    static String migratorUser;
    static String migratorPassword;
    static DataSource appDs;
    static DataSource migratorDs;

    private static boolean ready;

    private ProvisionedTenant() {
    }

    static synchronized void ensure() {
        if (ready) {
            return;
        }
        url = env("SIGSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/sigsan");
        appUser = env("SIGSAN_IT_DB_USER", "sigsan_app");
        appPassword = env("SIGSAN_IT_DB_PASSWORD", "sigsan_dev_password");
        appDs = new DriverManagerDataSource(url, appUser, appPassword);
        migratorUser = env("SIGSAN_IT_MIGRATOR_USER", "sigsan_migrator");
        migratorPassword = env("SIGSAN_IT_MIGRATOR_PASSWORD", "sigsan_migrator_password");
        migratorDs = new DriverManagerDataSource(url, migratorUser, migratorPassword);

        // 1. Migration de core em banco virgem (cria public.tenant_catalog e public.audit_log).
        new CoreMigrationRunner(migratorDs).run(null);

        // 2. Provisionamento: aplica todas as migrations de tenant de todos os módulos.
        TenantProvisioningService service = new TenantProvisioningService(
                appDs,
                new JdbcTemplate(appDs),
                Mockito.mock(AuditLogRepository.class),
                Mockito.mock(KeycloakAdminService.class),
                migratorDs);

        var result = service.provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(TENANT_ID)
                .schemaName(SCHEMA)
                .displayName("Concessionaria de Integracao")
                .cnpj("12345678000199")
                .email("it@teste.com.br")
                .phone("11999999999")
                .plan("PRO")
                // Com endereço e configurações de verdade: Map.toString() gerava "{street=...}", que o
                // cast ::jsonb recusa. Os testes antigos passavam null e nunca exercitaram isso.
                .address(java.util.Map.of("street", "Rua das Águas", "number", 100, "city", "Cidade \"Teste\""))
                .settings(java.util.Map.of("timezone", "America/Sao_Paulo", "billing", java.util.Map.of("dueDay", 10)))
                .adminUsername("admin")
                .adminEmail("admin@teste.com.br")
                .adminName("Administrador")
                // Hash bcrypt real (custo 12) da senha de teste "senha-de-teste-sigsan", gerado com
                // spring-security-crypto. O provisionamento recusa o que não for bcrypt completo.
                .adminPasswordHash("$2a$12$XQ.OOBGIj7Rx81rNhJ11Wu6ZC5MGgdgkVG5VHomMqrv2kzOSgP5pC")
                .build());

        assertThat(result.success())
                .as("o provisionamento do tenant com todos os módulos deve concluir: %s", result.error())
                .isTrue();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
                if (SCHEMA.startsWith("it_")) {
                    st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
                }
            } catch (Exception ignored) {
                // melhor esforço: o banco de integração é descartável
            }
        }));
        ready = true;
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
