package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.16: rate limit na cadeia real, com o store JDBC (tabela
 * {@code rate_limit_buckets}, core V5).
 *
 * <p>Standalone de propósito: NÃO estende {@link AppIntegrationTestBase}, que
 * desliga o limite — aqui o limite é 3/minuto, e a 4ª requisição do mesmo
 * tenant é 429 com {@code Retry-After}. O health segue isento.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(BffTestClientConfig.class)
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("ziipsan.db.url", () -> ProvisionedTenant.url);
        registry.add("ziipsan.db.app-user", () -> ProvisionedTenant.appUser);
        registry.add("ziipsan.db.app-password", () -> ProvisionedTenant.appPassword);
        registry.add("ziipsan.migrator.user", () -> ProvisionedTenant.migratorUser);
        registry.add("ziipsan.migrator.password", () -> ProvisionedTenant.migratorPassword);
        registry.add("ziipsan.db.migrate-on-startup", () -> "false");
        registry.add("ziipsan.oidc.issuer", () -> TestTokens.ISSUER);
        registry.add("ziipsan.oidc.audience", () -> TestTokens.AUDIENCE);
        registry.add("ziipsan.oidc.public-key", TestTokens::publicKeyBase64);
        registry.add("ziipsan.crypto.provider", () -> "dev-env");
        registry.add("ziipsan.crypto.dev-master-key", () -> AppIntegrationTestBase.DEV_MASTER_KEY);
        registry.add("spring.security.oauth2.client.registration.ziipsan-bff.client-secret", () -> "test-bff-secret");
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> "");
        registry.add("spring.security.oauth2.client.provider.keycloak.authorization-uri",
                () -> TestTokens.ISSUER + "/protocol/openid-connect/auth");
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> TestTokens.ISSUER + "/protocol/openid-connect/token");
        registry.add("spring.security.oauth2.client.provider.keycloak.jwk-set-uri",
                () -> TestTokens.ISSUER + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.client.provider.keycloak.user-info-uri",
                () -> TestTokens.ISSUER + "/protocol/openid-connect/userinfo");
        registry.add("spring.security.oauth2.client.provider.keycloak.user-name-attribute", () -> "preferred_username");
        // O limite deste teste: 3/minuto por chave.
        registry.add("ziipsan.rate-limit.enabled", () -> "true");
        registry.add("ziipsan.rate-limit.limit-per-minute", () -> "3");
        registry.add("ziipsan.rate-limit.window-seconds", () -> "60");
    }

    @Test
    @DisplayName("3 requisições do mesmo tenant passam; a 4ª é 429 com Retry-After")
    void fourthRequestIs429() throws Exception {
        String auth = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "OPERATOR"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/me").header("Authorization", auth))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/me").header("Authorization", auth))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:too-many-requests"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("health segue isento do limite")
    void healthIsExempt() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
