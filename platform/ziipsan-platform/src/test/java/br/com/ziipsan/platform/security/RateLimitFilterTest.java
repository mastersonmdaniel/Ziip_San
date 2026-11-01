package br.com.ziipsan.platform.security;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.16: o rate limit, sem Spring e sem banco (store em memória). */
class RateLimitFilterTest {

    private static final int LIMIT = 2;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("desligado, passa sempre")
    void disabledPasses() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), false, LIMIT, 60);
        for (int i = 0; i < 5; i++) {
            assertThat(run(filter, "/api/v1/registry/customers")).isTrue();
        }
    }

    @Test
    @DisplayName("dentro do limite passa; acima, 429 com Retry-After e type too-many-requests")
    void overLimitIs429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), true, LIMIT, 60);
        assertThat(run(filter, "/api/v1/registry/customers")).isTrue();
        assertThat(run(filter, "/api/v1/registry/customers")).isTrue();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/registry/customers"), response, (req, res) -> {
        });
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("too-many-requests");
        assertThat(response.getHeader("Retry-After")).isNotNull();
    }

    @Test
    @DisplayName("health é isento, mesmo acima do limite")
    void healthIsExempt() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new InMemoryRateLimitStore(), true, 1, 60);
        assertThat(run(filter, "/actuator/health")).isTrue();
        assertThat(run(filter, "/actuator/health")).isTrue();
        assertThat(run(filter, "/actuator/health")).isTrue();
    }

    @Test
    @DisplayName("store com falha: fail-open (passa, com aviso no log)")
    void storeFailureIsFailOpen() throws Exception {
        RateLimitStore failing = (key, windowStart) -> {
            throw new RuntimeException("banco fora");
        };
        RateLimitFilter filter = new RateLimitFilter(failing, true, 1, 60);
        assertThat(run(filter, "/api/v1/registry/customers")).isTrue();
    }

    @Test
    @DisplayName("chave: tenant antes de usuário, usuário antes de IP")
    void keyPrecedence() {
        MockHttpServletRequest request = request("/api/v1/registry/customers");
        UUID tenant = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenant);
        assertThat(RateLimitFilter.keyOf(request)).isEqualTo("tenant:" + tenant);
        TenantContextHolder.clear();

        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "usuario-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))));
        assertThat(RateLimitFilter.keyOf(request)).isEqualTo("user:usuario-1");
        SecurityContextHolder.clearContext();

        assertThat(RateLimitFilter.keyOf(request)).isEqualTo("ip:127.0.0.1");
    }

    @Test
    @DisplayName("janela nova zera a contagem (store em memória)")
    void newWindowResets() {
        InMemoryRateLimitStore store = new InMemoryRateLimitStore();
        assertThat(store.increment("k", 1000)).isEqualTo(1);
        assertThat(store.increment("k", 1000)).isEqualTo(2);
        assertThat(store.increment("k", 1060)).isEqualTo(1);
    }

    private static boolean run(RateLimitFilter filter, String uri) throws Exception {
        AtomicBoolean passed = new AtomicBoolean(false);
        filter.doFilter(request(uri), new MockHttpServletResponse(), (req, res) -> passed.set(true));
        return passed.get();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
