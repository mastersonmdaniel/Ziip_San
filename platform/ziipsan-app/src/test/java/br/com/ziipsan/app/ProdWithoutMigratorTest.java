package br.com.ziipsan.app;

import br.com.ziipsan.security.crypto.TenantKeyProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.6: o perfil {@code prod} sobe SEM o migrador e SEM a senha do migrador — e não migra.
 *
 * <p>Prova: contexto completo no ar, um único DataSource (o da app, funcional: SELECT 1
 * responde), e ausência dos três beans do perfil migrate ({@code migratorDataSource},
 * {@code databaseMigrator}, {@code tenantProvisioningService}). Sem o
 * {@code DatabaseMigrator}, nenhuma migração em processo pode rodar — a migração é um
 * job separado, que roda antes da api.
 *
 * <p>Detalhe honesto: o perfil prod recusa o provider de cifra dev-env (CryptoConfig) e
 * o provider de produção (Vault) ainda não existe. O teste registra um stub de
 * {@code TenantKeyProvider} e {@code ziipsan.crypto.provider=test-stub} SÓ para o boot
 * de prod ser possível; o stub nunca cifra nada aqui (nenhum documento é tocado) e não
 * enfraquece as asserções sobre o migrador, que são independentes da cifra.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("prod")
// @TestConfiguration aninhada NÃO é auto-registrada pelo @SpringBootTest: o @Import
// explícito é o que faz o stub entrar no contexto (sem ele, o FieldCipher falha por
// falta de provider, como a primeira execução deste teste provou).
@org.springframework.context.annotation.Import({ProdWithoutMigratorTest.StubKeyProvider.class, BffTestClientConfig.class})
class ProdWithoutMigratorTest extends MigrationProfileTestBase {

    @TestConfiguration
    static class StubKeyProvider {
        @Bean
        TenantKeyProvider tenantKeyProvider() {
            return Mockito.mock(TenantKeyProvider.class);
        }
    }

    @DynamicPropertySource
    static void cryptoStub(DynamicPropertyRegistry registry) {
        // Provider fictício só para o boot de prod (ver javadoc da classe). Nenhuma
        // propriedade do migrador é registrada: é exatamente isso que o teste prova.
        registry.add("ziipsan.crypto.provider", () -> "test-stub");
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HikariDataSource dataSource;

    @Test
    @DisplayName("prod: contexto sobe, só o DataSource da app existe e nenhum bean do migrador existe")
    void prodHasNoMigratorBeans() throws Exception {
        assertThat(context.getBeanNamesForType(HikariDataSource.class)).hasSize(1);
        assertThat(dataSource.getUsername()).isEqualTo(ProvisionedTenant.appUser);
        assertThat(context.containsBean("migratorDataSource")).isFalse();
        assertThat(context.containsBean("databaseMigrator")).isFalse();
        assertThat(context.containsBean("tenantProvisioningService")).isFalse();
        // O contexto não está só "de pé": o DataSource da app funciona de verdade.
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertThat(st.executeQuery("SELECT 1").next()).isTrue();
        }
    }
}
