package br.com.ziipsan.platform.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.43: o filtro de acesso a rota de plataforma, sem Spring e sem banco. */
class PlatformAccessFilterTest {

    private final PlatformAccessFilter filter = new PlatformAccessFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("fora de rota de plataforma, passa sempre (mesmo token de tenant)")
    void nonPlatformPasses() throws Exception {
        authenticate(List.of("VIEWER"), "11111111-1111-1111-1111-111111111111");
        assertThat(run("/api/v1/registry/customers").get()).isTrue();
    }

    @Test
    @DisplayName("token com tenant em rota de plataforma: 403 tenant-not-allowed (mesmo com papel de plataforma)")
    void tenantTokenIsRefusedOnPlatformRoute() throws Exception {
        authenticate(List.of("PLATFORM_OWNER"), "11111111-1111-1111-1111-111111111111");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/platform/deployments"), response, (req, res) -> {
        });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("tenant-not-allowed");
    }

    @Test
    @DisplayName("token sem tenant e sem papel de plataforma: 403 access-denied")
    void noPlatformRoleIsAccessDenied() throws Exception {
        for (List<String> roles : List.of(List.<String>of(), List.of("VIEWER"), List.of("ADMIN"))) {
            authenticate(roles, null);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/platform/deployments"), response, (req, res) -> {
            });
            assertThat(response.getStatus()).as("papéis %s", roles).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("access-denied");
        }
    }

    @Test
    @DisplayName("cada papel de plataforma, sem tenant, passa no filtro (a permissão fina é do controller)")
    void eachPlatformRolePasses() throws Exception {
        for (String role : List.of("PLATFORM_OWNER", "PLATFORM_OPS", "PLATFORM_SUPPORT", "PLATFORM_FINANCE")) {
            authenticate(List.of(role), null);
            assertThat(run("/api/v1/platform/deployments").get()).as("papel %s", role).isTrue();
        }
    }

    @Test
    @DisplayName("sem autenticação JWT, passa (quem dá 401 é a cadeia, não o filtro)")
    void noJwtPassesThrough() throws Exception {
        assertThat(run("/api/v1/platform/deployments").get()).isTrue();
    }

    private AtomicBoolean run(String uri) throws Exception {
        AtomicBoolean passed = new AtomicBoolean(false);
        filter.doFilter(request(uri), new MockHttpServletResponse(), (req, res) -> passed.set(true));
        return passed;
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static void authenticate(List<String> roles, String tenantId) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles", roles));
        if (tenantId != null) {
            builder.claim(JwtTenantFilter.TENANT_CLAIM, tenantId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
    }
}
