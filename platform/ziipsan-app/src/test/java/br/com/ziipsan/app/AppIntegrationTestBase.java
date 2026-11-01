package br.com.ziipsan.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Aplicação inteira (contexto Spring real, filtros de segurança reais) sobre o banco de integração
 * descartável e o tenant de {@link ProvisionedTenant}. Só o emissor dos tokens é do teste.
 *
 * <p>migrate-on-startup fica desligado: o banco de integração é compartilhado pelos módulos do build
 * e contém tenants de outros testes; o tenant usado aqui já foi migrado por ProvisionedTenant.
 */
@SpringBootTest(classes = ZiipsanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(BffTestClientConfig.class)
abstract class AppIntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ProvisionedTenant.ensure();
        registry.add("ziipsan.db.url", () -> ProvisionedTenant.url);
        registry.add("ziipsan.db.app-user", () -> ProvisionedTenant.appUser);
        registry.add("ziipsan.db.app-password", () -> ProvisionedTenant.appPassword);
        // P0.6: prefixo do migrador mudou (ziipsan.migrator.*); as variaveis de ambiente
        // dos testes continuam as mesmas (ProvisionedTenant as le de ZIIPSAN_IT_*).
        registry.add("ziipsan.migrator.user", () -> ProvisionedTenant.migratorUser);
        registry.add("ziipsan.migrator.password", () -> ProvisionedTenant.migratorPassword);
        registry.add("ziipsan.db.migrate-on-startup", () -> "false");
        registry.add("ziipsan.oidc.issuer", () -> TestTokens.ISSUER);
        registry.add("ziipsan.oidc.audience", () -> TestTokens.AUDIENCE);
        registry.add("ziipsan.oidc.public-key", TestTokens::publicKeyBase64);
        registry.add("ziipsan.crypto.provider", () -> "dev-env");
        registry.add("ziipsan.crypto.dev-master-key", () -> DEV_MASTER_KEY);
        // P0.14 (corrigido no turno 9): o client confidencial existe no contexto de teste
        // via BffTestClientConfig (endpoints EXPLÍCITOS, sem discovery — o Boot 4.1 chama
        // fromIssuerLocation sempre que issuer-uri != null, e "" explodia com
        // "issuer cannot be empty"). As properties abaixo continuam existindo só para o
        // binding de OAuth2ClientProperties não quebrar no placeholder ${ZIIPSAN_OIDC_ISSUER}
        // do yml; o repositório usado é o bean, não o auto-config. O segredo é de teste.
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

    /** Chave mestra de teste, gerada a cada JVM: nenhum dado cifrado aqui sobrevive ao teste nem vale fora dele. */
    static final String DEV_MASTER_KEY = randomKey();

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
