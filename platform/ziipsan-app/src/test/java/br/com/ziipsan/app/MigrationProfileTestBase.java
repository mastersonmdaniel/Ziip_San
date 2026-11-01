package br.com.ziipsan.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes de perfil da P0.6: só as propriedades da APLICAÇÃO (banco da app +
 * OIDC de teste). NENHUMA propriedade do migrador é registrada aqui — cada subclasse
 * decide se (e como) registra as do migrador, e o teste de prod/worker prova que o
 * contexto sobe sem elas.
 *
 * <p>Cada subclasse é um contexto Spring distinto (perfis diferentes), por isso o custo
 * é um boot completo por classe. O banco é o de integração descartável, via
 * {@link ProvisionedTenant} (que lê as senhas de ambiente, como o ci-linux exporta).
 */
abstract class MigrationProfileTestBase {

    @DynamicPropertySource
    static void appProperties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("ziipsan.db.url", () -> ProvisionedTenant.url);
        registry.add("ziipsan.db.app-user", () -> ProvisionedTenant.appUser);
        registry.add("ziipsan.db.app-password", () -> ProvisionedTenant.appPassword);
        registry.add("ziipsan.oidc.issuer", () -> TestTokens.ISSUER);
        registry.add("ziipsan.oidc.audience", () -> TestTokens.AUDIENCE);
        registry.add("ziipsan.oidc.public-key", TestTokens::publicKeyBase64);
        // P0.14 (BFF): mesmos overrides de AppIntegrationTestBase — endpoints explicitos,
        // sem discovery do Keycloak, segredo de teste. Ver o comentario la.
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
    }
}
