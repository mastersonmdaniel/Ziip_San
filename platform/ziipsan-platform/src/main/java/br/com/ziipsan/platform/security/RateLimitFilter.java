package br.com.ziipsan.platform.security;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Rate limit por chave (P0.16, 429 do plano).
 *
 * <p>Posição: DEPOIS do {@link JwtTenantFilter} nas duas cadeias, para ter o
 * tenant no contexto — requisições barradas pelo tenant (403) nem chegam aqui e
 * não consomem o balde. Chave, em ordem: {@code tenant:<uuid>} se houver tenant;
 * senão {@code user:<sub>} se houver autenticação; senão o IP. Isento:
 * {@code /actuator/health} (o health check não pode ser derrubado pelo próprio
 * limite).
 *
 * <p>Resposta: 429 {@code too-many-requests} em problem+json + header
 * {@code Retry-After} (segundos até o fim da janela). <b>Fail-open:</b> se o
 * store falhar, a requisição passa e o aviso vai para o log — o limite nunca
 * pode derrubar a API sozinho.
 *
 * <p>Não é {@code @Component}: senão o Spring Boot o registraria também como
 * filtro de servlet comum, fora da cadeia e antes da autenticação.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitStore store;
    private final boolean enabled;
    private final int limitPerMinute;
    private final int windowSeconds;

    public RateLimitFilter(RateLimitStore store, boolean enabled, int limitPerMinute, int windowSeconds) {
        this.store = store;
        this.enabled = enabled;
        this.limitPerMinute = limitPerMinute;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = pathOf(request);
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        long windowStart = now / windowSeconds * windowSeconds;
        int count;
        try {
            count = store.increment(keyOf(request), windowStart);
        } catch (RuntimeException e) {
            // Fail-open: sem balde, sem limite — mas com aviso no log.
            log.warn("Rate limit indisponível, permitindo a requisição: {}", e.toString());
            chain.doFilter(request, response);
            return;
        }
        if (count > limitPerMinute) {
            long retryAfter = Math.max(1, windowSeconds - (now - windowStart));
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            ProblemResponses.write(response, 429, "too-many-requests", "Muitas requisições",
                    "Limite de requisições por minuto excedido. Tente novamente em " + retryAfter + " segundos.");
            return;
        }
        chain.doFilter(request, response);
    }

    static String keyOf(HttpServletRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return "tenant:" + tenantId;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /** URI menos o contextPath (getServletPath() é vazio no mapeamento padrão "/" e no MockMvc). */
    static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
    }
}
