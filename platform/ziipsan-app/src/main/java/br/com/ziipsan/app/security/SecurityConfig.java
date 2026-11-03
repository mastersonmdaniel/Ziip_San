package br.com.ziipsan.app.security;

import br.com.ziipsan.app.config.ZiipsanProperties;
import br.com.ziipsan.platform.security.ContentLengthLimitFilter;
import br.com.ziipsan.platform.security.JdbcRateLimitStore;
import br.com.ziipsan.platform.security.JwtTenantFilter;
import br.com.ziipsan.platform.security.MfaGateFilter;
import br.com.ziipsan.platform.security.PlatformAccessFilter;
import br.com.ziipsan.platform.security.RateLimitFilter;
import br.com.ziipsan.platform.security.RateLimitStore;
import br.com.ziipsan.platform.security.PermissionResolver;
import br.com.ziipsan.platform.security.ProblemResponses;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.sql.DataSource;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Duas cadeias no mesmo processo {@code api} (P0.14), sem quebrar uma à outra:
 *
 * <ol>
 *   <li><b>BFF (ordem 1)</b> — SÓ para o navegador: casa {@code /bff/**} e as
 *       requisições a {@code /api/v1/**} que trazem o cookie de sessão. Sessão no
 *       servidor (Spring Session JDBC, core V5), CSRF ligado, login OIDC com o
 *       client confidencial {@code ziipsan-bff}, proteção contra fixação de sessão
 *       e logout que invalida a sessão no PostgreSQL e encerra o SSO no Keycloak.
 *       O {@link BffJwtReconstructionFilter} reconstrói o JWT do access token
 *       guardado no servidor, então o filtro de tenant e os
 *       {@code @PreAuthorize} veem o mesmo que veriam com Bearer;</li>
 *   <li><b>Resource server (ordem 2, INALTERADO em comportamento)</b> — Bearer
 *       para apps e integrações máquina-a-máquina: casa todo o resto, incluindo
 *       {@code /api/v1/**} sem cookie de sessão. STATELESS, sem CSRF (não há
 *       cookie de sessão), com os mesmos {@code permitAll} de antes
 *       ({@code /actuator/health} e {@code /v3/api-docs}).</li>
 * </ol>
 *
 * <p>A precedência é pelo cookie de sessão, não pelo caminho: um app que manda
 * Bearer continua caindo na cadeia 2 mesmo em {@code /api/v1/**}, e um navegador
 * sem sessão cai na cadeia 2 e recebe 401 (o frontend, então, vai para o login
 * BFF). O cookie de sessão é {@code __Host-} (Secure, HttpOnly, SameSite=Lax —
 * Lax, e não Strict, porque o retorno do Keycloak é um GET cross-site e o Strict
 * o perderia). Sem TLS (perfil {@code local}), o nome perde o prefixo e o Secure
 * é desligado EXPLICITAMENTE em {@code application-local.yml} — nunca em prod.
 */
@Configuration
@EnableMethodSecurity
// P0.14 (BFF): o Boot 4.1 NAO tem mais SessionAutoConfiguration (a propriedade
// spring.session.store-type virou inerte) — a sessao JDBC e ligada explicitamente
// aqui. Tabelas spring_session/spring_session_attributes no schema public, criadas
// pela migration core V5 (job migrate da P0.6, nunca pela api: o repositorio JDBC
// nao executa DDL). Timeout de inatividade: 1800s (30 minutos).
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SecurityConfig {

    /** Store do rate limit (P0.16): tabela compartilhada entre réplicas, via DataSource da aplicação. */
    @Bean
    public RateLimitStore rateLimitStore(DataSource dataSource) {
        return new JdbcRateLimitStore(dataSource);
    }

    private static RateLimitFilter rateLimitFilter(RateLimitStore store, ZiipsanProperties properties) {
        ZiipsanProperties.RateLimit cfg = properties.rateLimit();
        return new RateLimitFilter(store, cfg.enabled(), cfg.limitPerMinute(), cfg.windowSeconds());
    }

    private static ContentLengthLimitFilter bodyLimitFilter(ZiipsanProperties properties) {
        return new ContentLengthLimitFilter(properties.bodyLimit().maxBytes());
    }

    /**
     * Headers de superfície web (P0.16) nas duas cadeias: CSP estrita para
     * respostas da API (não são documentos) + HSTS (só emitido em requisição
     * segura; ver forward-headers-strategy em application.yml).
     */
    private static void webSurfaceHeaders(HttpSecurity http) throws Exception {
        http.headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                .httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000).includeSubDomains(true)));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain bffSecurity(HttpSecurity http, JwtDecoder jwtDecoder, TenantCatalog tenantCatalog,
                                           OAuth2AuthorizedClientRepository authorizedClients,
                                           ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManager,
                                           ClientRegistrationRepository clientRegistrations,
                                           ZiipsanProperties properties, RateLimitStore rateLimitStore,
                                           ObjectProvider<io.micrometer.tracing.Tracer> tracer) throws Exception {
        BffJwtReconstructionFilter reconstruction = new BffJwtReconstructionFilter(
                authorizedClients, authorizedClientManager.getIfAvailable(), jwtDecoder, jwtAuthenticationConverter());
        JwtTenantFilter tenantFilter = new JwtTenantFilter(tenantCatalog, tracer.getIfAvailable());
        // XHR para /api/v1 com sessão vencida recebe 401 em problem+json (o frontend
        // trata e vai para o login); navegação comum é redirecionada para /bff/login.
        AuthenticationEntryPoint bffEntryPoint = (request, response, ex) -> {
            if (request.getRequestURI().startsWith("/api/v1")) {
                ProblemResponses.write(response, 401, "unauthenticated", "Não autenticado",
                        "A sessão acabou. Entre novamente.");
            } else {
                response.sendRedirect("/bff/login");
            }
        };
        AccessDeniedHandler bffForbidden = (request, response, ex) -> ProblemResponses.write(
                response, 403, "access-denied", "Acesso negado",
                "O usuário não tem permissão para esta operação.");

        http.securityMatcher(bffMatcher(properties))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .cors(cors -> {
                })
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fix -> fix.migrateSession()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/bff/login", "/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/bff/**", "/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2Login(login -> login
                        .loginPage("/bff/login")
                        .successHandler(new BffLoginSuccessHandler()))
                .logout(logout -> logout
                        .logoutUrl("/bff/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrations)))
                .exceptionHandling(e -> e.authenticationEntryPoint(bffEntryPoint).accessDeniedHandler(bffForbidden))
                .addFilterAfter(reconstruction, OAuth2LoginAuthenticationFilter.class)
                .addFilterAfter(new MfaGateFilter(), BffJwtReconstructionFilter.class)
                .addFilterAfter(new PlatformAccessFilter(), MfaGateFilter.class)
                .addFilterAfter(tenantFilter, PlatformAccessFilter.class)
                .addFilterAfter(rateLimitFilter(rateLimitStore, properties), JwtTenantFilter.class)
                .addFilterBefore(bodyLimitFilter(properties), CsrfFilter.class);
        webSurfaceHeaders(http);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder jwtDecoder, TenantCatalog tenantCatalog,
                                           ZiipsanProperties properties, RateLimitStore rateLimitStore,
                                           ObjectProvider<io.micrometer.tracing.Tracer> tracer)
            throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> ProblemResponses.write(
                response, 401, "unauthenticated", "Não autenticado",
                "Envie um token de acesso válido no cabeçalho Authorization.");
        AccessDeniedHandler forbidden = (request, response, ex) -> ProblemResponses.write(
                response, 403, "access-denied", "Acesso negado",
                "O usuário não tem permissão para esta operação.");

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // P0.8: metricas e Prometheus existem, mas NAO sao publicos: exigem
                        // autenticacao (o scrape interno usa credencial; health continua aberto).
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**",
                                "/actuator/prometheus").authenticated()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .addFilterAfter(new MfaGateFilter(), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new PlatformAccessFilter(), MfaGateFilter.class)
                .addFilterAfter(new JwtTenantFilter(tenantCatalog, tracer.getIfAvailable()), PlatformAccessFilter.class)
                .addFilterAfter(rateLimitFilter(rateLimitStore, properties), JwtTenantFilter.class)
                .addFilterBefore(bodyLimitFilter(properties), BearerTokenAuthenticationFilter.class);
        webSurfaceHeaders(http);
        return http.build();
    }

    /**
     * Cadeia BFF casa: tudo em {@code /bff/**} (login, CSRF, logout) e
     * {@code /api/v1/**} SOMENTE quando o cookie de sessão está presente. Sem o
     * cookie, a requisição cai na cadeia resource server (Bearer ou 401).
     */
    static RequestMatcher bffMatcher(ZiipsanProperties properties) {
        String sessionCookie = properties.bff().cookieName();
        return (HttpServletRequest request) -> {
            // requestURI menos o contextPath (getServletPath() e vazio no mapeamento
            // padrao "/" do DispatcherServlet, e tambem no MockMvc).
            String uri = request.getRequestURI();
            String context = request.getContextPath();
            String path = context != null && !context.isEmpty() && uri.startsWith(context)
                    ? uri.substring(context.length()) : uri;
            if (path.startsWith("/bff/") || path.equals("/bff")) {
                return true;
            }
            // O fluxo oauth2Login vive nesta cadeia: sem casar estes caminhos, o
            // callback do Keycloak nunca seria processado.
            if (path.startsWith("/oauth2/authorization/") || path.startsWith("/login/oauth2/code/")) {
                return true;
            }
            if (path.startsWith("/api/v1")) {
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    return Arrays.stream(cookies).anyMatch(c -> sessionCookie.equals(c.getName()));
                }
            }
            return false;
        };
    }

    private static org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
            oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrations) {
        var handler = new org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler(
                clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}/login");
        return handler;
    }

    /** Serializador do cookie de sessão (Spring Session): nome, Secure, HttpOnly e SameSite do BFF. */
    @Bean
    public CookieSerializer cookieSerializer(ZiipsanProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(properties.bff().cookieName());
        serializer.setUseSecureCookie(properties.bff().cookieSecure());
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }

    /**
     * O filtro do Spring Session tem de envolver a requisição ANTES da cadeia
     * Spring Security (é ele que faz {@code request.getSession()} devolver a
     * sessão do PostgreSQL, usada pelo fluxo OAuth2 e pelo SecurityContext da
     * cadeia BFF). Sem esta ordem, a cadeia veria a sessão do container e a
     * sessão PG nunca seria usada em produção. O Boot não registra a ordem
     * sozinho (não há mais auto-config de sessão no Boot 4.1).
     */
    @Bean
    public FilterRegistrationBean<SessionRepositoryFilter<?>> sessionRepositoryFilterRegistration(
            SessionRepositoryFilter<?> filter) {
        FilterRegistrationBean<SessionRepositoryFilter<?>> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
        return registration;
    }

    @Bean
    public JwtDecoder jwtDecoder(ZiipsanProperties properties) throws Exception {
        ZiipsanProperties.Oidc oidc = properties.oidc();
        NimbusJwtDecoder decoder;
        if (oidc.publicKey() != null && !oidc.publicKey().isBlank()) {
            byte[] der = Base64.getDecoder().decode(oidc.publicKey().trim());
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            decoder = NimbusJwtDecoder.withPublicKey(key).build();
        } else if (oidc.jwkSetUri() != null && !oidc.jwkSetUri().isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwkSetUri()).build();
        } else {
            throw new IllegalStateException("Defina ziipsan.oidc.jwk-set-uri (ou ziipsan.oidc.public-key em teste)");
        }
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(oidc.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(oidc.issuer()), audience));
        return decoder;
    }

    /**
     * Papéis do realm (realm_access.roles) viram ROLE_* (compatibilidade, ex.: {@code /api/v1/me})
     * e, pela P0.13, também as permissões {@code recurso:ação} do catálogo: perfil de fábrica
     * expandido para suas permissões, permissão direta mantida como está. As rotas verificam as
     * permissões com {@code hasAuthority(...)}, nunca mais os papéis. O MESMO conversor serve
     * às duas cadeias (Bearer e BFF reconstruído).
     */
    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> roles = jwt -> {
            List<GrantedAuthority> out = new ArrayList<>();
            Object realmAccess = jwt.getClaims().get("realm_access");
            if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> list) {
                List<String> roleNames = new ArrayList<>();
                for (Object role : list) {
                    roleNames.add(String.valueOf(role));
                    out.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
                for (String permission : PermissionResolver.permissionsForRoles(roleNames)) {
                    out.add(new SimpleGrantedAuthority(permission));
                }
            }
            return out;
        };
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(ZiipsanProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
