package br.com.ziipsan.integration.web;

import br.com.ziipsan.integration.service.ApiTokenService;
import br.com.ziipsan.integration.service.PublicApiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API pública de integração (P2.26) — distinta da API do frontend.
 *
 * <p>Autenticação própria (cadeia de segurança da API pública): o token
 * endpoint emite tokens opacos por {@code client_credentials} e as rotas
 * de recurso exigem os escopos do token, não papéis de usuário. Erros
 * das rotas de recurso saem em problem+json (RFC 9457), pelo tratador
 * global; erros do token endpoint seguem o formato OAuth (RFC 6749).
 * Toda resposta carrega {@code X-Ziipsan-Public-Api-Version: 1} (filtro).
 *
 * <p>Versionamento: o caminho é {@code /api/v1/public-api/v1/...} — a
 * v1 da API pública dentro da v1 da plataforma (a guarda da P0.10 exige
 * toda rota sob {@code /api/v1}). Versão desconhecida é 404 (filtro);
 * versão descontinuada futura carrega {@code Deprecation} e
 * {@code Sunset} (ver {@code PublicApiVersions}).
 */
@RestController
@RequestMapping("/api/v1/public-api")
public class PublicApiController {

    public record PageView<T>(java.util.List<T> items, int page, int size, long total) {
    }

    public record CreateServiceOrderInput(String serviceTypeCode, UUID unitId) {
    }

    private final ApiTokenService tokens;
    private final PublicApiService api;

    public PublicApiController(ApiTokenService tokens, PublicApiService api) {
        this.tokens = tokens;
        this.api = api;
    }

    // ------------------------------------------------------------ token endpoint
    /**
     * Token endpoint OAuth 2.0 (permitAll na cadeia da API pública — ver
     * a allowlist do RoutePermissionTest, como {@code /api/v1/public/brand}:
     * é o endpoint que EMITE a credencial, não uma rota de negócio; a
     * autenticação do cliente é feita aqui, por segredo ou asserção).
     */
    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam Map<String, String> form, HttpServletRequest request) {
        String clientId = form.get("client_id");
        String clientSecret = form.get("client_secret");
        String basic = request.getHeader("Authorization");
        if (basic != null && basic.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(basic.substring("Basic ".length())),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon > 0) {
                    clientId = decoded.substring(0, colon);
                    clientSecret = decoded.substring(colon + 1);
                }
            } catch (IllegalArgumentException ignored) {
                // Basic malformado: cai para invalid_client abaixo.
            }
        }
        UUID tenantId = null;
        if (form.get("tenant_id") != null) {
            try {
                tenantId = UUID.fromString(form.get("tenant_id"));
            } catch (IllegalArgumentException ignored) {
                // tenant_id malformado: invalid_client abaixo.
            }
        }
        var req = new ApiTokenService.TokenRequest(tenantId, clientId, form.get("grant_type"),
                form.get("scope"), clientSecret, form.get("client_assertion_type"),
                form.get("client_assertion"));
        try {
            var issued = tokens.issue(req, request.getRemoteAddr());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", issued.accessToken());
            body.put("token_type", issued.tokenType());
            body.put("expires_in", issued.expiresIn());
            body.put("scope", issued.scope());
            return ResponseEntity.ok(body);
        } catch (ApiTokenService.OAuthException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.error());
            body.put("error_description", e.getMessage());
            return ResponseEntity.status(e.status()).body(body);
        }
    }

    // ------------------------------------------------------------------ reads
    @GetMapping("/v1/properties")
    @PreAuthorize("hasAuthority('properties:read')")
    public PageView<Map<String, Object>> properties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String registrationNumber) {
        var result = api.properties(page, size, status, registrationNumber);
        return new PageView<>(result.items(), result.page(), result.size(), result.total());
    }

    @GetMapping("/v1/properties/{id}")
    @PreAuthorize("hasAuthority('properties:read')")
    public Map<String, Object> property(@PathVariable UUID id) {
        return api.property(id);
    }

    @GetMapping("/v1/bills")
    @PreAuthorize("hasAuthority('bills:read')")
    public PageView<Map<String, Object>> bills(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status) {
        var result = api.bills(page, size, unitId, status);
        return new PageView<>(result.items(), result.page(), result.size(), result.total());
    }

    @GetMapping("/v1/bills/{id}")
    @PreAuthorize("hasAuthority('bills:read')")
    public Map<String, Object> bill(@PathVariable UUID id) {
        return api.bill(id);
    }

    @GetMapping("/v1/payments")
    @PreAuthorize("hasAuthority('payments:read')")
    public PageView<Map<String, Object>> payments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) String status) {
        var result = api.payments(page, size, unitId, status);
        return new PageView<>(result.items(), result.page(), result.size(), result.total());
    }

    @GetMapping("/v1/payments/{id}")
    @PreAuthorize("hasAuthority('payments:read')")
    public Map<String, Object> payment(@PathVariable UUID id) {
        return api.payment(id);
    }

    @GetMapping("/v1/service-orders")
    @PreAuthorize("hasAuthority('service-orders:read')")
    public PageView<Map<String, Object>> serviceOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        var result = api.serviceOrders(page, size, status);
        return new PageView<>(result.items(), result.page(), result.size(), result.total());
    }

    @GetMapping("/v1/service-orders/{id}")
    @PreAuthorize("hasAuthority('service-orders:read')")
    public Map<String, Object> serviceOrder(@PathVariable UUID id) {
        return api.serviceOrder(id);
    }

    @PostMapping("/v1/service-orders")
    @PreAuthorize("hasAuthority('service-orders:write')")
    public ResponseEntity<Map<String, Object>> createServiceOrder(@RequestBody CreateServiceOrderInput in) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(api.createServiceOrder(in.serviceTypeCode(), in.unitId()));
    }
}
