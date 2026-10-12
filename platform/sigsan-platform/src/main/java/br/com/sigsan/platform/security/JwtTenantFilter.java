package br.com.sigsan.platform.security;

import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.platform.tenant.TenantCatalog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant da requisição a partir do token, nunca do cliente (achado C3 do PDCA de 12/10/2026).
 *
 * <p>Antes, o TenantFilter do core aceitava o schema ({@code X-Tenant-ID}) e o UUID
 * ({@code X-Tenant-UUID}) enviados pelo navegador, sem ligação com o usuário autenticado: quem
 * mandasse os cabeçalhos escolhia o tenant. Agora:
 * <ol>
 *   <li>o UUID vem do claim {@value #TENANT_CLAIM} do JWT já validado (assinatura, emissor,
 *       audiência, validade);</li>
 *   <li>o schema vem de public.tenant_catalog pelo UUID, e o tenant precisa estar ACTIVE;</li>
 *   <li>os cabeçalhos antigos, se vierem, precisam coincidir; divergência é 403.</li>
 * </ol>
 *
 * <p>Fica na cadeia do Spring Security, depois do BearerTokenAuthenticationFilter. Não é
 * {@code @Component}: senão o Spring Boot o registraria também como filtro de servlet comum, fora da
 * cadeia e antes da autenticação.
 */
public class JwtTenantFilter extends OncePerRequestFilter {

    public static final String TENANT_CLAIM = "tenant_id";
    static final String LEGACY_SCHEMA_HEADER = "X-Tenant-ID";
    static final String LEGACY_UUID_HEADER = "X-Tenant-UUID";

    private final TenantCatalog catalog;

    public JwtTenantFilter(TenantCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            // Sem JWT (ex.: /actuator/health). Rotas protegidas já foram barradas pela autorização.
            chain.doFilter(request, response);
            return;
        }
        Jwt jwt = jwtAuth.getToken();

        UUID tenantId = parseUuid(jwt.getClaimAsString(TENANT_CLAIM));
        if (tenantId == null) {
            ProblemResponses.write(response, 403, "tenant-ausente", "Tenant ausente",
                    "O token não identifica o tenant (claim " + TENANT_CLAIM + ").");
            return;
        }
        Optional<TenantCatalog.Entry> entry = catalog.find(tenantId);
        if (entry.isEmpty() || !entry.get().active()) {
            ProblemResponses.write(response, 403, "tenant-inativo", "Tenant indisponível",
                    "O tenant do token não existe ou não está ativo.");
            return;
        }
        String schema = entry.get().schema();

        String headerSchema = request.getHeader(LEGACY_SCHEMA_HEADER);
        String headerUuid = request.getHeader(LEGACY_UUID_HEADER);
        if ((headerSchema != null && !headerSchema.equals(schema))
                || (headerUuid != null && !headerUuid.equalsIgnoreCase(tenantId.toString()))) {
            ProblemResponses.write(response, 403, "tenant-divergente", "Tenant divergente",
                    "O cabeçalho de tenant não corresponde ao tenant do token.");
            return;
        }

        try {
            TenantContextHolder.setTenant(schema);
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setUserId(parseUuid(jwt.getSubject()));
            MDC.put("tenant", tenantId.toString());
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("tenant");
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
