package br.com.ziipsan.app.web;

import br.com.ziipsan.app.config.ZiipsanProperties;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.domain.JdbcTenantDomainStore;
import br.com.ziipsan.platform.domain.TenantDomainService;
import br.com.ziipsan.platform.domain.TenantDomainService.CreateResult;
import br.com.ziipsan.platform.domain.TenantDomainStore.DomainView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * Administração do domínio próprio do tenant (P2.27, PLT-05).
 *
 * <p>Permissão fina {@code brand:manage} (reuso, como a marca da
 * P1.19 — o domínio próprio é o parâmetro {@code brand.custom_domain}):
 * nenhuma permissão nova no catálogo, logo nenhum perfil de fábrica a
 * atribuir (a CI que recusa permissão sem perfil não é acionada). O
 * tenant vem do token, nunca do corpo.
 *
 * <p>Rotas: POST cria o pedido (devolve o token TXT UMA vez — o admin
 * publica {@code _ziipsan-verify.<host>} e aponta o CNAME para a
 * borda); POST {@code /{id}/verify} confere TXT + CNAME e emite o
 * certificado de teste (local) ou o da borda (LC.2); POST
 * {@code /{id}/renew} renova na janela de 30 dias; POST
 * {@code /{id}/revoke} revoga certificado e domínio; DELETE remove o
 * apontamento (o mesmo método que o ciclo de vida chama no
 * encerramento do contrato). Sem {@code ziipsan.domains.edge-host}
 * configurado, POST e verify falham com mensagem clara; GET funciona.
 */
@RestController
@RequestMapping("/api/v1/tenant/domains")
@PreAuthorize("hasAuthority('brand:manage')")
public class TenantDomainController {

    public record CreateDomainRequest(String hostname) {
    }

    public record CreateDomainResponse(DomainView domain, String token, String txtName, String txtValue,
                                       String cnameTarget) {
    }

    public record RevokeRequest(String reason) {
    }

    private final TenantDomainService service;

    public TenantDomainController(DataSource dataSource, ZiipsanProperties properties) {
        this.service = new TenantDomainService(new JdbcTenantDomainStore(dataSource),
                TenantDomainService.local(properties.domains().edgeHost()));
    }

    @PostMapping
    public ResponseEntity<CreateDomainResponse> create(@RequestBody CreateDomainRequest in,
                                                       JwtAuthenticationToken auth) {
        if (in.hostname() == null || in.hostname().isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "hostname", "empty", "O domínio é obrigatório."));
        }
        CreateResult result = service.requestDomain(tenantId(), actorId(auth), in.hostname());
        return ResponseEntity.status(201).body(new CreateDomainResponse(
                result.domain(), result.token(), result.txtName(), result.txtValue(), result.cnameTarget()));
    }

    @GetMapping
    public List<DomainView> list() {
        return service.listDomains(tenantId());
    }

    @PostMapping("/{id}/verify")
    public DomainView verify(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return service.verifyDomain(tenantId(), actorId(auth), id);
    }

    @PostMapping("/{id}/renew")
    public DomainView renew(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return service.renewDomain(tenantId(), actorId(auth), id);
    }

    @PostMapping("/{id}/revoke")
    public DomainView revoke(@PathVariable UUID id, @RequestBody(required = false) RevokeRequest in,
                             JwtAuthenticationToken auth) {
        String reason = in == null || in.reason() == null ? "ADMIN" : in.reason();
        return service.revokeDomain(tenantId(), actorId(auth), id, reason);
    }

    @DeleteMapping("/{id}")
    public DomainView release(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return service.releaseDomain(tenantId(), actorId(auth), id, "ADMIN_REMOVED");
    }

    private static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        return tenantId;
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }
}
