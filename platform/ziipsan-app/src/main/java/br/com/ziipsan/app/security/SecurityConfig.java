package br.com.ziipsan.app.security;

import br.com.ziipsan.app.config.ZiipsanProperties;
import br.com.ziipsan.platform.security.JwtTenantFilter;
import br.com.ziipsan.platform.security.ProblemResponses;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * API protegida por JWT do Keycloak (realm único "ziipsan"), sem sessão nem cookie.
 *
 * <p>O token é validado por assinatura, emissor, audiência ({@code ziipsan-api}) e validade. Depois da
 * autenticação, o {@link JwtTenantFilter} fixa o tenant a partir do claim {@code tenant_id}. Sem CSRF
 * porque não há cookie de sessão: a credencial é o cabeçalho Authorization.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder jwtDecoder, TenantCatalog tenantCatalog)
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
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .addFilterAfter(new JwtTenantFilter(tenantCatalog), BearerTokenAuthenticationFilter.class);
        return http.build();
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

    /** Papéis do realm (realm_access.roles) viram ROLE_*, para {@code hasRole(...)}. */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        Converter<Jwt, Collection<GrantedAuthority>> roles = jwt -> {
            List<GrantedAuthority> out = new ArrayList<>();
            Object realmAccess = jwt.getClaims().get("realm_access");
            if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> list) {
                for (Object role : list) {
                    out.add(new SimpleGrantedAuthority("ROLE_" + role));
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
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
