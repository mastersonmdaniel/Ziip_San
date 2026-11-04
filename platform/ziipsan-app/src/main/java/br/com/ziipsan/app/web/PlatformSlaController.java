package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.support.SupportSlaService;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Políticas de SLA de suporte (P2.15; ADR-0020, ADR-0025), lado do operador.
 *
 * <p>Acesso: token de plataforma. A política é termo do contrato, por
 * isso reusa as permissões de contrato da P2.12 — {@code contract:manage}
 * (OWNER e FINANCE) para criar e alterar, {@code contract:read} (os quatro
 * papéis) para ler — em vez de uma permissão nova sem perfil de fábrica
 * (a CI da P1.37 recusa permissão sem perfil). O filtro de plataforma já
 * recusou token com tenant aqui.
 *
 * <p>A criação é em dois passos, como o contrato: primeiro a política
 * (chave, calendário, nível, janela), depois os cinco alvos S1–S5 — sem
 * os cinco, a aplicação ao chamado é recusada pelo serviço.
 */
@RestController
@RequestMapping("/api/v1/platform/sla")
public class PlatformSlaController {

    public record MatrixInput(String impact, String criticality, String severity) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;

    public PlatformSlaController(DataSource dataSource, TenantCatalog catalog) {
        this.dataSource = dataSource;
        this.catalog = catalog;
    }

    private SupportSlaService service(UUID tenantId) {
        catalog.find(tenantId)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new SupportSlaService(dataSource, tenantId, Clock.systemUTC());
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String platformRole(JwtAuthenticationToken auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name.startsWith("ROLE_PLATFORM_")) {
                return name.substring("ROLE_".length());
            }
        }
        return null;
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SupportSlaService.PolicyView createPolicy(@RequestParam UUID tenantId,
                                                     @RequestBody SupportSlaService.PolicyInput in,
                                                     JwtAuthenticationToken auth) {
        return service(tenantId).createPolicy(actorId(auth), platformRole(auth), in);
    }

    @PostMapping("/policies/{policyKey}/targets")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SupportSlaService.PolicyView setTargets(@RequestParam UUID tenantId,
                                                   @PathVariable String policyKey,
                                                   @RequestBody List<SupportSlaService.TargetInput> targets,
                                                   JwtAuthenticationToken auth) {
        return service(tenantId).setTargets(policyKey, targets, actorId(auth), platformRole(auth));
    }

    @PostMapping("/policies/{policyKey}/matrix")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SupportSlaService.PolicyView setMatrixCell(@RequestParam UUID tenantId,
                                                      @PathVariable String policyKey,
                                                      @RequestBody MatrixInput in,
                                                      JwtAuthenticationToken auth) {
        return service(tenantId).setMatrixCell(policyKey, in.impact(), in.criticality(), in.severity(),
                actorId(auth), platformRole(auth));
    }

    @GetMapping("/policies/{policyKey}")
    @PreAuthorize("hasAuthority('contract:read')")
    public SupportSlaService.PolicyView getPolicy(@RequestParam UUID tenantId, @PathVariable String policyKey) {
        return service(tenantId).policy(policyKey);
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('contract:read')")
    public List<SupportSlaService.PolicyView> listPolicies(@RequestParam UUID tenantId) {
        return service(tenantId).policies();
    }

    @GetMapping("/policies/{policyKey}/classify")
    @PreAuthorize("hasAuthority('contract:read')")
    public String classify(@RequestParam UUID tenantId, @PathVariable String policyKey,
                           @RequestParam String impact, @RequestParam String criticality) {
        return service(tenantId).classifyForPolicy(policyKey, impact, criticality);
    }
}
