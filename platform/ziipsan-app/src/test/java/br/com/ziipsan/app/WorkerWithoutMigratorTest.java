package br.com.ziipsan.app;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.6: o perfil {@code worker} sobe SEM o migrador e SEM a senha do migrador.
 *
 * <p>Mesma prova do teste de prod (um único DataSource funcional, nenhum dos três beans
 * do perfil migrate), mas sem o stub de cifra: "worker" não contém "prod", então o
 * provider dev-env de teste é aceito normalmente.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("worker")
// P0.14: idem MigrateRunsMigrationTest — registro BFF explicito para o auto-config
// do Boot nao tentar o discovery com o issuer-uri vazio de teste.
@org.springframework.context.annotation.Import(BffTestClientConfig.class)
class WorkerWithoutMigratorTest extends MigrationProfileTestBase {

    @DynamicPropertySource
    static void cryptoForTest(DynamicPropertyRegistry registry) {
        registry.add("ziipsan.crypto.provider", () -> "dev-env");
        registry.add("ziipsan.crypto.dev-master-key", WorkerWithoutMigratorTest::randomKey);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HikariDataSource dataSource;

    @Test
    @DisplayName("worker: contexto sobe, só o DataSource da app existe e nenhum bean do migrador existe")
    void workerHasNoMigratorBeans() throws Exception {
        assertThat(context.getBeanNamesForType(HikariDataSource.class)).hasSize(1);
        assertThat(dataSource.getUsername()).isEqualTo(ProvisionedTenant.appUser);
        assertThat(context.containsBean("migratorDataSource")).isFalse();
        assertThat(context.containsBean("databaseMigrator")).isFalse();
        assertThat(context.containsBean("tenantProvisioningService")).isFalse();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertThat(st.executeQuery("SELECT 1").next()).isTrue();
        }
    }
}
