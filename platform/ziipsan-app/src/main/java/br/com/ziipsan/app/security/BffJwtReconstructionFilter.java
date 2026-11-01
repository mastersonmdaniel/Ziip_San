package br.com.ziipsan.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ponte entre a cadeia BFF e o resto da API (P0.14).
 *
 * <p>O navegador autentica por sessão (OAuth2 login) e nunca vê token; mas o
 * {@link br.com.ziipsan.platform.security.JwtTenantFilter} e o {@code MeController} só
 * tratam {@code JwtAuthenticationToken}. Em vez de refatorá-los (e arriscar a cadeia
 * Bearer, que atende apps e máquinas), este filtro reconstrói o JWT a partir do
 * access token guardado no servidor (cliente autorizado da sessão):
 * <ol>
 *   <li>se houver um {@code OAuth2AuthorizedClientManager}, ele é usado primeiro: ele
 *       renova o access token com o refresh token quando venceu (rotação no Keycloak,
 *       {@code revokeRefreshToken=true} no client confidencial);</li>
 *   <li>senão, usa o cliente autorizado guardado na sessão;</li>
 *   <li>o JWT decodificado passa pelo MESMO conversor da cadeia Bearer
 *       (papéis do realm + permissões {@code recurso:ação} da P0.13), então o filtro
 *       de tenant e os {@code @PreAuthorize} veem exatamente o que veriam com Bearer;</li>
 *   <li>se o access token não trouxer {@code amr} (P0.15), o claim é completado com o
 *       do id token da sessão, quando houver — o gate de MFA lê o {@code amr} do JWT
 *       reconstruído, sem distinguir BFF de Bearer.</li>
 * </ol>
 *
 * <p>Só atua em {@code /api/v1/**} e só quando a autenticação é OAuth2 (sessão BFF).
 * Token vencido ou inválido: limpa o contexto e deixa a cadeia decidir (a entrada
 * do oauth2Login redireciona o navegador para o login); nunca vira 500.
 */
public class BffJwtReconstructionFilter extends OncePerRequestFilter {

    public static final String REGISTRATION_ID = "ziipsan-bff";
    static final String AMR_CLAIM = "amr";

    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter;

    public BffJwtReconstructionFilter(OAuth2AuthorizedClientRepository authorizedClients,
                                      OAuth2AuthorizedClientManager authorizedClientManager,
                                      JwtDecoder jwtDecoder,
                                      JwtAuthenticationConverter converter) {
        this.authorizedClients = authorizedClients;
        this.authorizedClientManager = authorizedClientManager;
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken oauth && requestPath(request).startsWith("/api/v1")) {
            OAuth2AuthorizedClient client = authorizedClient(oauth, request);
            OAuth2AccessToken accessToken = client == null ? null : client.getAccessToken();
            if (accessToken != null) {
                try {
                    Jwt jwt = jwtDecoder.decode(accessToken.getTokenValue());
                    Jwt merged = mergeAmrFromIdToken(jwt, oauth);
                    // convert() devolve AbstractAuthenticationToken (na prática, um
                    // JwtAuthenticationToken): reaproveitam-se as authorities dele.
                    AbstractAuthenticationToken converted = converter.convert(merged);
                    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(merged,
                            converted == null ? List.of() : converted.getAuthorities()));
                } catch (JwtException e) {
                    // Sessão com token vencido/inválido: volta a não autenticado; a entrada
                    // do BFF redireciona para o login em vez de estourar 500.
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static String requestPath(HttpServletRequest request) {
        // Nao usar getServletPath(): no mapeamento padrao do DispatcherServlet ("/") ele
        // e vazio e o caminho vem em getPathInfo(); requestURI menos o contextPath vale
        // nos dois casos (e no MockMvc).
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private OAuth2AuthorizedClient authorizedClient(OAuth2AuthenticationToken oauth, HttpServletRequest request) {
        if (authorizedClientManager != null) {
            OAuth2AuthorizedClient refreshed = authorizedClientManager.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID).principal(oauth).build());
            if (refreshed != null) {
                return refreshed;
            }
        }
        return authorizedClients.loadAuthorizedClient(REGISTRATION_ID, oauth, request);
    }

    private static Jwt mergeAmrFromIdToken(Jwt jwt, OAuth2AuthenticationToken oauth) {
        if (jwt.hasClaim(AMR_CLAIM)) {
            return jwt;
        }
        Object amr = null;
        if (oauth.getPrincipal() instanceof OidcUser oidc && oidc.getIdToken() != null) {
            amr = oidc.getIdToken().getClaim(AMR_CLAIM);
        }
        if (amr == null) {
            return jwt;
        }
        Map<String, Object> claims = new HashMap<>(jwt.getClaims());
        claims.put(AMR_CLAIM, amr);
        return new Jwt(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), claims);
    }
}
