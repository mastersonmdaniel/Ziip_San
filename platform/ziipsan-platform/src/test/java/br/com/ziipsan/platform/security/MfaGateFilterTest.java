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

/** P0.15: o gate de segundo fator, sem Spring e sem banco. */
class MfaGateFilterTest {

    private final MfaGateFilter filter = new MfaGateFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("fora de rota de plataforma, passa sempre (mesmo sem amr)")
    void nonPlatformPasses() throws Exception {
        authenticate(List.of());
        AtomicBoolean passed = run("/api/v1/registry/customers");
        assertThat(passed.get()).isTrue();
    }

    @Test
    @DisplayName("rota de plataforma com amr otp passa; com hwk passa")
    void platformWithMfaAmrPasses() throws Exception {
        for (List<String> amr : List.of(List.of("otp"), List.of("hwk"), List.of("pwd", "otp"))) {
            authenticate(amr);
            assertThat(run("/api/v1/platform/qualquer").get()).as("amr %s", amr).isTrue();
        }
    }

    @Test
    @DisplayName("rota de plataforma sem amr, ou só com pwd, é 403 mfa-required")
    void platformWithoutMfaAmrIs403() throws Exception {
        for (List<String> amr : List.of(List.<String>of(), List.of("pwd"))) {
            authenticate(amr);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/platform/qualquer"), response, (req, res) -> {
            });
            assertThat(response.getStatus()).as("amr %s", amr).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("mfa-required");
        }
        // Sem o claim amr: mesmo resultado.
        SecurityContextHolder.getContext().setAuthentication(jwtToken(Map.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/platform/qualquer"), response, (req, res) -> {
        });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("mfa-required");
    }

    @Test
    @DisplayName("sem autenticação JWT, passa (quem dá 401 é a cadeia, não o gate)")
    void noJwtPassesThrough() throws Exception {
        assertThat(run("/api/v1/platform/qualquer").get()).isTrue();
    }

    private AtomicBoolean run(String uri) throws Exception {
        AtomicBoolean passed = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> passed.set(true);
        filter.doFilter(request(uri), new MockHttpServletResponse(), chain);
        return passed;
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static void authenticate(List<String> amr) {
        SecurityContextHolder.getContext().setAuthentication(jwtToken(Map.of("amr", amr)));
    }

    private static JwtAuthenticationToken jwtToken(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claims(c -> c.putAll(claims)).build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
