package br.com.ziipsan.platform.security;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTenantFilterTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SCHEMA = "tenant_alpha";

    private TenantCatalog catalog;
    private JwtTenantFilter filter;

    @BeforeEach
    void setUp() {
        catalog = mock(TenantCatalog.class);
        when(catalog.find(any())).thenReturn(Optional.empty());
        when(catalog.find(TENANT)).thenReturn(Optional.of(new TenantCatalog.Entry(TENANT, SCHEMA, "ACTIVE")));
        filter = new JwtTenantFilter(catalog);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private static void authenticate(String tenantClaim) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(USER.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (tenantClaim != null) {
            jwt.claim(JwtTenantFilter.TENANT_CLAIM, tenantClaim);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    /** Executa o filtro e devolve o contexto de tenant visto DENTRO da cadeia (ou null se a cadeia não rodou). */
    private String[] run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String[]> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(new String[]{
                TenantContextHolder.getTenant(),
                String.valueOf(TenantContextHolder.getTenantId()),
                String.valueOf(TenantContextHolder.getUserId())});
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    @DisplayName("claim válido: schema do catálogo, UUID e usuário no contexto durante a requisição")
    void validClaimPopulatesContext() throws Exception {
        authenticate(TENANT.toString());
        String[] ctx = run(new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(ctx).containsExactly(SCHEMA, TENANT.toString(), USER.toString());
    }

    @Test
    @DisplayName("cabeçalho legado igual ao token passa")
    void matchingHeaderPasses() throws Exception {
        authenticate(TENANT.toString());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", SCHEMA);
        request.addHeader("X-Tenant-UUID", TENANT.toString());
        assertThat(run(request, new MockHttpServletResponse())).isNotNull();
    }

    @Test
    @DisplayName("cabeçalho de outro tenant: 403 e a cadeia não roda")
    void divergentHeaderIs403() throws Exception {
        authenticate(TENANT.toString());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant_beta");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(run(request, response)).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("tenant-mismatch");
    }

    @Test
    @DisplayName("sem claim ou claim inválido: 403 tenant-missing")
    void missingClaimIs403() throws Exception {
        for (String claim : new String[]{null, "", "nao-e-uuid"}) {
            authenticate(claim);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(run(new MockHttpServletRequest(), response)).isNull();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("tenant-missing");
        }
    }

    @Test
    @DisplayName("tenant suspenso ou inexistente: 403 tenant-inactive")
    void inactiveTenantIs403() throws Exception {
        UUID suspended = UUID.randomUUID();
        when(catalog.find(suspended)).thenReturn(Optional.of(new TenantCatalog.Entry(suspended, "tenant_s", "SUSPENDED")));
        for (UUID t : new UUID[]{suspended, UUID.randomUUID()}) {
            authenticate(t.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(run(new MockHttpServletRequest(), response)).isNull();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("tenant-inactive");
        }
    }

    @Test
    @DisplayName("o contexto é limpo depois da requisição, mesmo com erro na cadeia")
    void contextIsClearedAfterRequest() throws Exception {
        authenticate(TENANT.toString());
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("falha no controller");
        };
        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (IllegalStateException expected) {
            // a exceção sobe; o que importa é o finally
        }
        assertThat(TenantContextHolder.getTenant()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(TenantContextHolder.getUserId()).isNull();
    }

    @Test
    @DisplayName("P0.43: rota de plataforma é isenta — token sem tenant passa sem tenant-missing (quem fiscaliza é o PlatformAccessFilter)")
    void platformPathIsExempt() throws Exception {
        authenticate(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform/deployments");
        request.setRequestURI("/api/v1/platform/deployments");
        String[] ctx = run(request, new MockHttpServletResponse());
        assertThat(ctx).containsExactly(null, "null", "null");
    }

    @Test
    @DisplayName("sem JWT (ex.: health): segue sem tenant")
    void noJwtPassesThrough() throws Exception {
        String[] ctx = run(new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(ctx).containsExactly(null, "null", "null");
    }

    @Test
    @DisplayName("P0.8: com Tracer, o span corrente ganha a tag tenant_id; sem Tracer, nada muda")
    void spanGetsTenantIdTagWhenTracerPresent() throws Exception {
        io.micrometer.tracing.Tracer tracer = mock(io.micrometer.tracing.Tracer.class);
        io.micrometer.tracing.Span span = mock(io.micrometer.tracing.Span.class);
        org.mockito.Mockito.when(tracer.currentSpan()).thenReturn(span);
        JwtTenantFilter withTracer = new JwtTenantFilter(catalog, tracer);
        authenticate(TENANT.toString());
        java.util.concurrent.atomic.AtomicReference<String[]> seen = new java.util.concurrent.atomic.AtomicReference<>();
        withTracer.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> seen.set(new String[]{TenantContextHolder.getTenant()}));
        assertThat(seen.get()).containsExactly(SCHEMA);
        org.mockito.Mockito.verify(span).tag(JwtTenantFilter.TENANT_CLAIM, TENANT.toString());
    }
}
