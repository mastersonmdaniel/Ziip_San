package br.com.sigsan.app;

import br.com.sigsan.core.migration.CoreMigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A aplicação sobe e a API só aceita token válido, com o tenant vindo do token (achado C3 do PDCA).
 */
class SecurityIntegrationTest extends AppIntegrationTestBase {

    private static final String PROBLEM = "application/problem+json";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HikariDataSource dataSource;

    @Autowired
    @Qualifier("migratorDataSource")
    private HikariDataSource migratorDataSource;

    @Test
    @DisplayName("contexto: dois DataSources com usuários distintos, sem Flyway do Boot e sem runner de migração automático")
    void contextWiring() {
        assertThat(dataSource.getUsername()).isEqualTo(ProvisionedTenant.appUser);
        assertThat(migratorDataSource.getUsername()).isEqualTo(ProvisionedTenant.migratorUser);
        assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
        assertThat(context.getBeanNamesForType(CoreMigrationRunner.class)).isEmpty();
    }

    @Test
    @DisplayName("health responde sem token")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sem token: 401 em problem+json")
    void noTokenIs401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:sigsan:problema:nao-autenticado"));
    }

    @Test
    @DisplayName("token válido: /api/me devolve o tenant do token e os papéis")
    void validTokenSetsTenant() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER", "OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(ProvisionedTenant.TENANT_ID.toString()))
                .andExpect(jsonPath("$.tenantSchema").value(ProvisionedTenant.SCHEMA))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.roles[1]").value("VIEWER"));
    }

    @Test
    @DisplayName("aud como string única (como o Keycloak emite) é aceito")
    void singleAudienceStringIsAccepted() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("VIEWER"), b -> b.audience(TestTokens.AUDIENCE));
        mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("usuario.teste"));
    }

    @Test
    @DisplayName("assinatura de outra chave, emissor errado, audiência errada ou expirado: 401")
    void invalidTokensAre401() throws Exception {
        UUID t = ProvisionedTenant.TENANT_ID;
        List<String> invalid = List.of(
                TestTokens.signedWithOtherKey(t),
                TestTokens.token(t, List.of("VIEWER"), b -> b.issuer("http://localhost:8080/realms/outro")),
                TestTokens.token(t, List.of("VIEWER"), b -> b.audience("outra-api")),
                TestTokens.token(t, List.of("VIEWER"), b -> b
                        .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                        .expirationTime(Date.from(Instant.now().minusSeconds(1800)))));
        for (String token : invalid) {
            mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        }
    }

    @Test
    @DisplayName("token sem tenant_id: 403 tenant-ausente")
    void tokenWithoutTenantIs403() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(TestTokens.token(null, "VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sigsan:problema:tenant-ausente"));
    }

    @Test
    @DisplayName("tenant do token fora do catálogo: 403 tenant-inativo")
    void unknownTenantIs403() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(TestTokens.token(UUID.randomUUID(), "VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sigsan:problema:tenant-inativo"));
    }

    @Test
    @DisplayName("cabeçalho de tenant diferente do token: 403; igual ao token: aceito")
    void legacyHeaderMustMatchToken() throws Exception {
        String token = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));
        mvc.perform(get("/api/me").header("Authorization", token).header("X-Tenant-ID", "outro_tenant"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sigsan:problema:tenant-divergente"));
        mvc.perform(get("/api/me").header("Authorization", token).header("X-Tenant-UUID", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me").header("Authorization", token).header("X-Tenant-ID", ProvisionedTenant.SCHEMA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("X-Request-ID: devolvido se bem formado, gerado se ausente")
    void requestIdIsEchoed() throws Exception {
        String token = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));
        mvc.perform(get("/api/me").header("Authorization", token).header("X-Request-ID", "req-12345678"))
                .andExpect(header().string("X-Request-ID", "req-12345678"));
        mvc.perform(get("/api/me").header("Authorization", token))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    @DisplayName("rota inexistente com token: 404 em problem+json, sem detalhe interno")
    void unknownRouteIs404Problem() throws Exception {
        mvc.perform(get("/api/nao-existe").header("Authorization", bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
