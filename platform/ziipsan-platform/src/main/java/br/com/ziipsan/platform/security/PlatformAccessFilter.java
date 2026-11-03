package br.com.ziipsan.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Fiscalização de rota de plataforma (P0.43, CENTRAL-ZIIP §2 e §10).
 *
 * <p>Posição: DEPOIS do {@link MfaGateFilter} e ANTES do {@link JwtTenantFilter}, nas
 * duas cadeias. A ordem importa e é a mesma nas duas:
 * <ol>
 *   <li>sem {@code amr} de segundo fator, o gate já devolveu 403 {@code mfa-required};</li>
 *   <li>aqui, token COM {@code tenant_id} em rota de plataforma é recusado com 403
 *       {@code tenant-not-allowed} — o token de plataforma não tem tenant, e o token
 *       de tenant não abre rota de plataforma (salvo acesso assistido/emergência,
 *       que é P3.17 e não passa por este prefixo);</li>
 *   <li>token sem nenhum papel {@code PLATFORM_*} é recusado com 403
 *       {@code access-denied} — o mesmo código do tratador de acesso negado;</li>
 *   <li>só então o {@link JwtTenantFilter} roda — e ele ISENTA rotas de plataforma,
 *       porque o token de plataforma legítimo não tem tenant e cairia em
 *       {@code tenant-missing} sem a isenção (interação documentada na P0.15 e
 *       resolvida aqui).</li>
 * </ol>
 * <p>Sem autenticação JWT, o filtro deixa passar: quem dá 401 é a cadeia. Fora de
 * rota de plataforma, deixa passar sempre. O filtro NÃO decide a permissão fina
 * da rota (isso é {@code @PreAuthorize} com a permissão do catálogo); ele só
 * garante as duas invariantes do §2 que valem para todo o prefixo.
 */
public class PlatformAccessFilter extends OncePerRequestFilter {

    /** Prefixo de rota de plataforma — o mesmo que o gate usa (P0.15). */
    public static final String PLATFORM_PATH_PREFIX = "/api/v1/platform/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        if (!path.startsWith(PLATFORM_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }
        String tenantId = jwtAuth.getToken().getClaimAsString(JwtTenantFilter.TENANT_CLAIM);
        if (tenantId != null && !tenantId.isBlank()) {
            ProblemResponses.write(response, 403, "tenant-not-allowed", "Token de tenant em rota de plataforma",
                    "Rotas de plataforma exigem token sem tenant (papel PLATFORM_*).");
            return;
        }
        if (!hasPlatformRole(jwtAuth.getToken().getClaims().get("realm_access"))) {
            ProblemResponses.write(response, 403, "access-denied", "Acesso negado",
                    "O usuário não tem papel de plataforma para esta operação.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean hasPlatformRole(Object realmAccess) {
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (PlatformRole.isPlatformRole(String.valueOf(role))) {
                    return true;
                }
            }
        }
        return false;
    }
}
