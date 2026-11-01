package br.com.ziipsan.app;

import br.com.ziipsan.app.security.BffJwtReconstructionFilter;
import br.com.ziipsan.app.security.SecurityConfig;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0.14: a ponte BFF -> JWT, sem Spring e sem banco. O decodificador é de verdade
 * (RS256 com a chave de teste); o repositório de clientes autorizados é falso.
 */
class BffJwtReconstructionFilterTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final JwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) TestTokens.KEYS.getPublic()).build();
    private final OAuth2AuthorizedClientRepository repository = mock(OAuth2AuthorizedClientRepository.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sessão OAuth2 em /api/v1 vira JwtAuthenticationToken com o tenant e os papéis do token")
    void oauthSessionBecomesJwt() throws Exception {
        String token = TestTokens.token(TENANT, "VIEWER");
        OAuth2AuthenticationToken oauth = oauthToken(Map.of());
        when(repository.loadAuthorizedClient(any(), any(), any())).thenReturn(authorizedClient(token));
        SecurityContextHolder.getContext().setAuthentication(oauth);

        new BffJwtReconstructionFilter(repository, null, decoder, SecurityConfig.jwtAuthenticationConverter())
                .doFilter(request("/api/v1/me"), new MockHttpServletResponse(), chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(((JwtAuthenticationToken) auth).getToken().getClaimAsString("tenant_id")).isEqualTo(TENANT.toString());
        assertThat(auth.getAuthorities().stream().map(a -> a.getAuthority()).toList()).contains("ROLE_VIEWER");
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("amr ausente no access token é completado com o do id token (gate da P0.15)")
    void amrMergedFromIdToken() throws Exception {
        String token = TestTokens.token(TENANT, "VIEWER");
        OAuth2AuthenticationToken oauth = oauthToken(Map.of("amr", List.of("otp")));
        when(repository.loadAuthorizedClient(any(), any(), any())).thenReturn(authorizedClient(token));
        SecurityContextHolder.getContext().setAuthentication(oauth);

        new BffJwtReconstructionFilter(repository, null, decoder, SecurityConfig.jwtAuthenticationConverter())
                .doFilter(request("/api/v1/me"), new MockHttpServletResponse(), chain);

        var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getToken().getClaimAsStringList("amr")).containsExactly("otp");
    }

    @Test
    @DisplayName("manager presente: o token renovado por ele é o usado (rotação), não o guardado")
    void managerRefreshWins() throws Exception {
        String stored = TestTokens.token(TENANT, "VIEWER");
        String refreshed = TestTokens.token(TENANT, "ADMIN");
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(authorizedClient(refreshed));
        when(repository.loadAuthorizedClient(any(), any(), any())).thenReturn(authorizedClient(stored));
        SecurityContextHolder.getContext().setAuthentication(oauthToken(Map.of()));

        new BffJwtReconstructionFilter(repository, manager, decoder, SecurityConfig.jwtAuthenticationConverter())
                .doFilter(request("/api/v1/me"), new MockHttpServletResponse(), chain);

        var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getToken().getTokenValue()).isEqualTo(refreshed);
        assertThat(auth.getAuthorities()).contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("sem cliente autorizado, a autenticação passa intacta (o filtro de tenant decide depois)")
    void noAuthorizedClientPassesThrough() throws Exception {
        OAuth2AuthenticationToken oauth = oauthToken(Map.of());
        when(repository.loadAuthorizedClient(any(), any(), any())).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(oauth);

        new BffJwtReconstructionFilter(repository, null, decoder, SecurityConfig.jwtAuthenticationConverter())
                .doFilter(request("/api/v1/me"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(oauth);
    }

    @Test
    @DisplayName("token vencido limpa o contexto (a entrada do BFF redireciona para o login, nunca 500)")
    void expiredTokenClearsContext() throws Exception {
        String token = TestTokens.token(TENANT, List.of("VIEWER"),
                b -> b.expirationTime(Date.from(Instant.now().minusSeconds(60))));
        OAuth2AuthenticationToken oauth = oauthToken(Map.of());
        when(repository.loadAuthorizedClient(any(), any(), any())).thenReturn(authorizedClient(token));
        SecurityContextHolder.getContext().setAuthentication(oauth);

        new BffJwtReconstructionFilter(repository, null, decoder, SecurityConfig.jwtAuthenticationConverter())
                .doFilter(request("/api/v1/me"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("fora de /api/v1 (ex.: /bff/csrf) o filtro não toca na autenticação OAuth2")
    void outsideApiPassesThrough() throws Exception {
        OAuth2AuthenticationToken oauth = oauthToken(Map.of());
        SecurityContextHolder.getContext().setAuthentication(oauth);

        new BffJwtReconstructionFilter(repository, null, decoder, SecurityConfig.jwtAuthenticationConverter())
                .doFilter(request("/bff/csrf"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(oauth);
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static OAuth2AuthenticationToken oauthToken(Map<String, Object> idTokenClaims) {
        Map<String, Object> claims = new java.util.HashMap<>(idTokenClaims);
        claims.put("sub", UUID.randomUUID().toString());
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300), claims);
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_VIEWER")), idToken);
        return new OAuth2AuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_VIEWER")),
                BffJwtReconstructionFilter.REGISTRATION_ID);
    }

    private static OAuth2AuthorizedClient authorizedClient(String tokenValue) {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
                Instant.now(), Instant.now().plusSeconds(300));
        return new OAuth2AuthorizedClient(
                ClientRegistration.withRegistrationId("ziipsan-bff")
                        .clientId("ziipsan-bff")
                        .authorizationGrantType(
                                new org.springframework.security.oauth2.core.AuthorizationGrantType("authorization_code"))
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .authorizationUri("http://localhost:8080/realms/ziipsan/protocol/openid-connect/auth")
                        .tokenUri("http://localhost:8080/realms/ziipsan/protocol/openid-connect/token")
                        .build(),
                // Security 7: o cliente guarda só o NOME do principal, não o Authentication.
                "usuario-teste",
                accessToken);
    }
}
