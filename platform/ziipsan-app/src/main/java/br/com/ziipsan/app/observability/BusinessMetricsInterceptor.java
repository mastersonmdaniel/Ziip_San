package br.com.ziipsan.app.observability;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * P0.8: alimenta {@link BusinessMetrics} uma vez por requisição de negócio.
 *
 * <p>É interceptor MVC (não filtro de servlet) de propósito: ele roda DEPOIS do
 * JwtTenantFilter, quando o tenant já está no TenantContextHolder, e depois do mapeamento
 * da rota. O grupo é o primeiro segmento após {@code /api/v1} — agregado, nunca a rota
 * completa (que incluiria IDs) e nunca o usuário. Requisições fora de {@code /api/v1}
 * (health, docs, BFF) não contam aqui.
 */
@Component
public class BusinessMetricsInterceptor implements HandlerInterceptor {

    private final BusinessMetrics metrics;

    public BusinessMetricsInterceptor(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String group = routeGroup(uri);
        if (group != null) {
            metrics.record(group, TenantContextHolder.getTenantId());
        }
        return true;
    }

    static String routeGroup(String uri) {
        if (uri == null) {
            return null;
        }
        String path = uri;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (!path.startsWith("/api/v1/")) {
            return null;
        }
        String rest = path.substring("/api/v1/".length());
        if (rest.isBlank()) {
            return null;
        }
        return rest.split("/", 2)[0];
    }
}
