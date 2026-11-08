package br.com.ziipsan.app;

import br.com.ziipsan.app.config.ZiipsanProperties;
import br.com.ziipsan.app.security.SecurityConfig;
import br.com.ziipsan.platform.security.RateLimitStore;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cadeia {@code apiSecurity} e o backoffice do DPO (correção R2-G2 da rodada 2,
 * 08/11/2026): antes da correção, {@code /privacidade/**} não casava nenhuma
 * regra da cadeia catch-all e batia no {@code anyRequest().denyAll()} — 403
 * para todo mundo, inclusive o encarregado, sem que o {@code @PreAuthorize}
 * do {@code DpoBackofficeController} fosse avaliado. A correção acrescenta
 * {@code .requestMatchers("/privacidade/**").authenticated()} à
 * {@code apiSecurity}, deixando o {@code @PreAuthorize} decidir.
 *
 * <p>Este teste constrói a cadeia REAL — o método {@code apiSecurity} da
 * {@code SecurityConfig} de verdade, com o {@code AuthorizationFilter} real
 * do Spring Security, o {@code BearerTokenAuthenticationFilter} real (com um
 * {@code JwtDecoder} mockado que devolve um JWT válido) e o entry point real
 * — e a atravessa com {@code MockHttpServletRequest/Response} e uma cadeia
 * downstream que registra a travessia. Os cinco filtros próprios do projeto
 * ({@code MfaGateFilter}, {@code PlatformAccessFilter}, {@code JwtTenantFilter},
 * {@code RateLimitFilter}, {@code ContentLengthLimitFilter}) NÃO são objeto
 * deste teste: não mudam com a correção e têm cobertura própria (o
 * comportamento real de tenant, ex.: 403 tenant-inactive, é provado pelo
 * {@code BffChainIntegrationTest} na CI). No harness eles são dublês
 * pass-through; a {@code ProblemResponses} é reimplementação fiel do
 * comportamento observável (status + problem+json + URN do tipo) e o
 * {@code PermissionResolver} devolve conjunto vazio (a regra sob teste é
 * {@code isAuthenticated()}, que não precisa de autoridade). O
 * {@code @PreAuthorize} do controller NÃO é exercido aqui (não há controller
 * no caminho) — ele é provado pelo {@code DpoBackofficeAuthorizationTest}
 * (teste S3) — e a travessia da cadeia no app composto é provada pelo
 * {@code PrivacidadeChainIntegrationTest} na CI (404 hoje — sem handler, LGPD
 * fora do scan — e NÃO 403; o 200 de ponta a ponta aguarda o scan do módulo).
 *
 * <p>Limitação declarada: este teste prova a REGRA da cadeia, não o
 * comportamento do filtro de tenant nem do MFA no app composto — esses vivem
 * na CI total.
 */
class PrivacidadeChainTest {

    /** Cadeia downstream que registra a travessia e responde 200. */
    private static final class Reached implements FilterChain {
        boolean reached;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            reached = true;
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_OK);
        }
    }

    private record Run(MockHttpServletResponse response, boolean reached) {
    }

    private static ZiipsanProperties testProperties() {
        return new ZiipsanProperties(
                new ZiipsanProperties.Db("jdbc:postgresql://localhost:5432/ziipsan", "app", "segredo", false),
                new ZiipsanProperties.Oidc("https://issuer.teste", null, null, "ziipsan-api"),
                new ZiipsanProperties.Cors(List.of()),
                new ZiipsanProperties.Bff("__Host-ziipsan-session", true),
                // P0.16: limite DESLIGADO, como a base de testes de integração faz.
                new ZiipsanProperties.RateLimit(false, 1000, 60),
                new ZiipsanProperties.BodyLimit(1048576L),
                new ZiipsanProperties.Clamav(null, null, null, null),
                new ZiipsanProperties.Storage(null, null),
                new ZiipsanProperties.Scanner(null, null, null),
                new ZiipsanProperties.Domains(null));
    }

    private static Jwt testJwt() {
        Instant now = Instant.now();
        return new Jwt("token-teste", now, now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "usuario-teste",
                        "tenant_id", UUID.randomUUID().toString(),
                        "realm_access", Map.of("roles", List.of("VIEWER"))));
    }

    /**
     * Constrói a cadeia REAL chamando o método {@code apiSecurity} da
     * {@code SecurityConfig} de verdade, sem contexto Spring: o
     * {@code HttpSecurity} é montado à mão, como a suíte do próprio Spring
     * Security faz. O {@code JwtDecoder} devolve o JWT de teste para qualquer
     * token (ou lança {@code BadJwtException}, no decodificador quebrado).
     */
    private static SecurityFilterChain realChain(JwtDecoder decoder) throws Exception {
        ObjectPostProcessor<Object> postProcessor = new ObjectPostProcessor<>() {
            @Override
            public <O> O postProcess(O object) {
                return object;
            }
        };
        AuthenticationManagerBuilder authBuilder = new AuthenticationManagerBuilder(postProcessor);
        HttpSecurity http = new HttpSecurity(postProcessor, authBuilder, new HashMap<>());
        // Sem contexto, o CorsConfigurer não acharia fonte: registra uma fonte
        // que devolve null (CORS não é objeto deste teste; nada é bloqueado).
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("corsConfigurationSource",
                (CorsConfigurationSource) request -> null);
        http.setSharedObject(ApplicationContext.class, context);
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        return new SecurityConfig().apiSecurity(http, decoder, mock(TenantCatalog.class),
                testProperties(), mock(RateLimitStore.class), tracerProvider);
    }

    private static SecurityFilterChain chainWithValidToken() throws Exception {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenReturn(testJwt());
        return realChain(decoder);
    }

    private static SecurityFilterChain chainWithBrokenDecoder() throws Exception {
        JwtDecoder decoder = mock(JwtDecoder.class);
        // Token malformado/expirado/assinatura inválida: o decodificador real
        // (Nimbus) lança BadJwtException, que vira InvalidBearerTokenException
        // e cai no entry point (401). Um JwtException genérico, ao contrário,
        // vira AuthenticationServiceException — e o AuthenticationEntryPointFailureHandler
        // do Spring o RELANÇA por desenho (falha de serviço, não de credencial);
        // por isso o mock lança BadJwtException aqui, não JwtException.
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("token inválido"));
        return realChain(decoder);
    }

    /**
     * Executa os filtros da cadeia em ordem, como o {@code FilterChainProxy} faz
     * (a {@code SecurityFilterChain} em si só expõe {@code getFilters()}): cada
     * filtro recebe uma cadeia que avança para o próximo, até o downstream.
     */
    private static void runFilters(SecurityFilterChain chain, ServletRequest request,
                                   ServletResponse response, FilterChain terminal)
            throws jakarta.servlet.ServletException, java.io.IOException {
        FilterChain current = terminal;
        java.util.List<Filter> filters = chain.getFilters();
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter filter = filters.get(i);
            FilterChain next = current;
            current = (req, res) -> filter.doFilter(req, res, next);
        }
        current.doFilter(request, response);
    }

    private static Run run(SecurityFilterChain chain, String method, String path, String bearer) throws Exception {
        // Isolamento por requisição: a cadeia montada à mão não inclui o
        // SecurityContextHolderFilter (ele vive no filtro composto do Boot),
        // então o contexto autenticado de um teste anterior vazaria para o
        // próximo no mesmo thread. Limpar antes e depois equivale ao que o
        // filtro faz por requisição no app real.
        SecurityContextHolder.clearContext();
        try {
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            if (bearer != null) {
                request.addHeader("Authorization", "Bearer " + bearer);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            Reached downstream = new Reached();
            runFilters(chain, request, response, downstream);
            return new Run(response, downstream.reached);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("R2-G2: anônimo em /privacidade/relatorio recebe 401 (não 403) — a regra é authenticated(), não denyAll")
    void anonymousGets401Not403() throws Exception {
        Run run = run(chainWithValidToken(), "GET", "/privacidade/relatorio", null);

        assertThat(run.reached()).as("a cadeia não deixa o anônimo chegar ao downstream").isFalse();
        assertThat(run.response().getStatus()).as("entry point: anônimo é 401, não 403 do denyAll").isEqualTo(401);
        assertThat(run.response().getContentType()).contains("application/problem+json");
        assertThat(run.response().getContentAsString()).contains("urn:ziipsan:problem:unauthenticated");
    }

    @Test
    @DisplayName("R2-G2: Bearer válido atravessa a cadeia nos três endpoints do backoffice do DPO")
    void authenticatedBearerReachesAllThreeDpoEndpoints() throws Exception {
        SecurityFilterChain chain = chainWithValidToken();

        for (String path : List.of("/privacidade/relatorio", "/privacidade/pendencias-cobertura",
                "/privacidade/operacoes")) {
            Run run = run(chain, "GET", path, "token-valido");
            assertThat(run.reached()).as("Bearer válido deve atravessar a cadeia em %s", path).isTrue();
            assertThat(run.response().getStatus()).as("downstream responde 200 em %s", path).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("R2-G2: token inválido recebe 401 — a cadeia continua exigindo Bearer válido")
    void invalidTokenGets401() throws Exception {
        Run run = run(chainWithBrokenDecoder(), "GET", "/privacidade/relatorio", "token-invalido");

        assertThat(run.reached()).as("token inválido não chega ao downstream").isFalse();
        assertThat(run.response().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("R2-G2: o catch-all continua negando — Bearer válido em caminho sem regra recebe 403")
    void denyAllStillDeniesUnmatchedPaths() throws Exception {
        Run run = run(chainWithValidToken(), "GET", "/qualquer-outro-caminho", "token-valido");

        assertThat(run.reached()).as("caminho sem regra não chega ao downstream").isFalse();
        assertThat(run.response().getStatus()).as("denyAll terminal intacto").isEqualTo(403);
        assertThat(run.response().getContentAsString()).contains("urn:ziipsan:problem:access-denied");
    }

    @Test
    @DisplayName("R2-G2: regras vizinhas intactas — /api/v1/** autenticado atravessa, /actuator/health é público")
    void neighbouringRulesIntact() throws Exception {
        SecurityFilterChain chain = chainWithValidToken();

        Run api = run(chain, "GET", "/api/v1/me", "token-valido");
        assertThat(api.reached()).as("/api/v1/** continua authenticated()").isTrue();

        Run health = run(chain, "GET", "/actuator/health", null);
        assertThat(health.reached()).as("/actuator/health continua permitAll").isTrue();
    }
}
