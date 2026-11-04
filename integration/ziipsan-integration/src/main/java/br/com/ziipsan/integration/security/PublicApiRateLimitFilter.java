package br.com.ziipsan.integration.security;

import br.com.ziipsan.integration.PublicApiScopes;
import br.com.ziipsan.integration.service.ApiRateLimiter;
import br.com.ziipsan.integration.service.IntegrationContext;
import br.com.ziipsan.integration.service.TenantParameterReader;
import br.com.ziipsan.platform.security.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limite de taxa da API pública (P2.26), depois da autenticação.
 *
 * <p>Por cliente autenticado: o menor entre o limite do cliente, o
 * parâmetro {@code api.rate_limit_per_minute} do tenant e o teto da
 * plataforma. Acima do limite: 429 problem+json com {@code Retry-After}.
 * O token endpoint (sem cliente autenticado) passa pelo filtro global
 * de taxa da cadeia (chave por IP, P0.16) — sem isso, a emissão de
 * tokens seria o ponto de consumo irrestrito da API (OWASP API Security
 * Top 10, API4). Falha do balde (banco indisponível) é fail-open com
 * log, como o {@code RateLimitFilter} da P0.16 — indisponibilidade do
 * contador não pode derrubar a API inteira.
 */
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimiter limiter;
    private final TenantParameterReader parameters;

    public PublicApiRateLimitFilter(ApiRateLimiter limiter, TenantParameterReader parameters) {
        this.limiter = limiter;
        this.parameters = parameters;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PublicApiAuthFilter.pathOf(request).startsWith(PublicApiAuthFilter.PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiClientAuthentication auth) {
            int limit = effectiveLimit(request, auth);
            ApiRateLimiter.Check check;
            try {
                check = limiter.check(auth.clientUuid(), limit);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(PublicApiRateLimitFilter.class)
                        .warn("Rate limit da API pública indisponível, permitindo a requisição: {}", e.toString());
                chain.doFilter(request, response);
                return;
            }
            if (!check.allowed()) {
                response.setHeader("Retry-After", String.valueOf(check.retryAfterSeconds()));
                ProblemResponses.write(response, 429, "too-many-requests", "Muitas requisições",
                        "Limite de requisições por minuto do cliente excedido. Tente novamente em "
                                + check.retryAfterSeconds() + " segundos.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private int effectiveLimit(HttpServletRequest request, ApiClientAuthentication auth) {
        Integer tenantLimit = null;
        try {
            tenantLimit = parameters.intValue(IntegrationContext.tenantId(), IntegrationContext.schema(),
                    "api.rate_limit_per_minute");
        } catch (RuntimeException ignored) {
            // Sem parâmetro legível, vale o teto.
        }
        int effective = PublicApiScopes.PLATFORM_RATE_LIMIT_PER_MINUTE;
        if (auth.rateLimitPerMinute() != null) {
            effective = Math.min(effective, auth.rateLimitPerMinute());
        }
        if (tenantLimit != null) {
            effective = Math.min(effective, tenantLimit);
        }
        return effective;
    }
}
