package br.com.ziipsan.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Registro do client BFF nos testes, com endpoints EXPLÍCITOS e sem discovery (P0.14, corrigido no turno 9).
 *
 * <p>Por que não via properties: o Boot 4.1 chama
 * {@code ClientRegistrations.fromIssuerLocation} sempre que {@code issuer-uri != null} — e a base
 * de teste sombreava o placeholder do yml com "" para evitar o discovery, o que no Security 7
 * explode com "issuer cannot be empty" na subida do contexto. Com este bean, o auto-config do
 * repositório recua ({@code @ConditionalOnMissingBean}) e o mapper (e o discovery) nunca rodam.
 * As properties de teste continuam existindo só para o binding de {@code OAuth2ClientProperties}
 * não quebrar no placeholder {@code ${ZIIPSAN_OIDC_ISSUER}} do yml.
 *
 * <p>O segredo é de teste, sem valor real. Sem Keycloak aqui: os endpoints nunca são chamados
 * (o login BFF de verdade é provado no E2E Playwright, fora do MockMvc).
 */
@TestConfiguration
class BffTestClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("ziipsan-bff")
                .clientId("ziipsan-bff")
                .clientSecret("test-bff-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(TestTokens.ISSUER + "/protocol/openid-connect/auth")
                .tokenUri(TestTokens.ISSUER + "/protocol/openid-connect/token")
                .jwkSetUri(TestTokens.ISSUER + "/protocol/openid-connect/certs")
                .userInfoUri(TestTokens.ISSUER + "/protocol/openid-connect/userinfo")
                .userNameAttributeName("preferred_username")
                .clientName("ziipsan-bff")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
