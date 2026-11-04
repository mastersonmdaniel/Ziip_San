package br.com.ziipsan.integration.security;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.integration.PublicApiVersions;
import br.com.ziipsan.integration.service.ApiTokenService;
import br.com.ziipsan.integration.service.TenantParameterReader;
import br.com.ziipsan.integration.security.CidrCheck;
import br.com.ziipsan.platform.security.ProblemResponses;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Autenticação da API pública (P2.26) — cadeia de segurança própria,
 * distinta da API do frontend e do BFF.
 *
 * <p>Para {@code /api/v1/public-api/v1/**}: exige Bearer <redacted> opaco emitido
 * pelo token endpoint; resolve o tenant embutido no token pelo catálogo
 * (tenant precisa estar ativo); valida o token no schema do tenant;
 * confere a allowlist de IP da requisição; monta o contexto do tenant e
 * a autenticação com os escopos do token. Sem token ou com token
 * inválido, 401 problem+json — sem distinguir os motivos.
 *
 * <p>Também aplica o versionamento: segmento {@code /v{n}/} desconhecido
 * é 404; versão descontinuada (hoje nenhuma) carrega {@code Deprecation}
 * e {@code Sunset}. Toda resposta da API pública carrega
 * {@code X-Ziipsan-Public-Api-Version: 1} e, em sandbox,
 * {@code X-Ziipsan-Sandbox: true}.
 */
public class PublicApiAuthFilter extends OncePerRequestFilter {

    public static final String PREFIX = "/api/v1/public-api/";

    private final ApiTokenService tokens;
    private final TenantCatalog catalog;
    private final TenantParameterReader parameters;
    private final PublicApiVersions versions;

    public PublicApiAuthFilter(ApiTokenService tokens, TenantCatalog catalog,
                               TenantParameterReader parameters, PublicApiVersions versions) {
        this.tokens = tokens;
        this.catalog = catalog;
        this.parameters = parameters;
        this.versions = versions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        // O token endpoint tem autenticação própria (OAuth) e é permitAll na cadeia.
        return !path.startsWith(PREFIX) || path.startsWith(PREFIX + "oauth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathOf(request);
        response.setHeader("X-Ziipsan-Public-Api-Version", String.valueOf(PublicApiVersions.CURRENT));
        Integer version = versionOf(path);
        if (version != null) {
            if (!versions.isKnown(version)) {
                ProblemResponses.write(response, 404, "not-found", "Não encontrado",
                        "A versão da API pública não existe.");
                return;
            }
            versions.sunsetFor(version).ifPresent(sunset -> {
                response.setHeader("Deprecation", "true");
                response.setHeader("Sunset", sunset);
            });
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            ProblemResponses.write(response, 401, "unauthenticated", "Não autenticado",
                    "Envie um token de acesso válido no cabeçalho Authorization.");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        UUID tenantId = ApiTokenService.tokenTenant(token);
        if (tenantId == null) {
            ProblemResponses.write(response, 401, "unauthenticated", "Não autenticado",
                    "Envie um token de acesso válido no cabeçalho Authorization.");
            return;
        }
        var entry = catalog.find(tenantId);
        if (entry.isEmpty() || !entry.get().active()) {
            ProblemResponses.write(response, 401, "unauthenticated", "Não autenticado",
                    "Envie um token de acesso válido no cabeçalho Authorization.");
            return;
        }
        String schema = entry.get().schema();
        try {
            TenantContextHolder.setTenant(schema);
            TenantContextHolder.setTenantId(tenantId);
            MDC.put("tenant", tenantId.toString());
            ApiClientAuthentication auth = tokens.validate(token, schema, tenantId);
            if (auth == null) {
                ProblemResponses.write(response, 401, "unauthenticated", "Não autenticado",
                        "Envie um token de acesso válido no cabeçalho Authorization.");
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> clientIps = auth.getDetails() instanceof List<?> list
                    ? (List<String>) list : List.of();
            List<String> allowedIps = clientIps.isEmpty()
                    ? parameters.stringListValue(tenantId, schema, "api.ip_allowlist")
                    : clientIps;
            if (!CidrCheck.allowed(request.getRemoteAddr(), allowedIps)) {
                ProblemResponses.write(response, 403, "access-denied", "Acesso negado",
                        "O IP de origem não está na allowlist do cliente.");
                return;
            }
            if (auth.isSandbox()) {
                response.setHeader("X-Ziipsan-Sandbox", "true");
            }
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("tenant");
        }
    }

    /** Versão do segmento {@code /v{n}/} do caminho, ou nulo se não há segmento. */
    static Integer versionOf(String path) {
        String rest = path.substring(PREFIX.length());
        if (!rest.startsWith("v")) {
            return null;
        }
        int slash = rest.indexOf('/');
        String number = slash < 0 ? rest.substring(1) : rest.substring(1, slash);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
    }
}
