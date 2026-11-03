package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.console.PlatformConsoleService;
import br.com.ziipsan.platform.console.TenantParameterService;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tela do tenant para propor, aprovar e aceitar mudança de parâmetro depois
 * da implantação (P1.6, sobre a V1_5 da P0.19), mais o painel de implantação
 * visível ao cliente e o gate de pronto para produção.
 *
 * <p>Autenticação: token de tenant (o filtro já recusou token de plataforma
 * aqui e resolveu o schema pelo catálogo). A separação proponente ≠ aprovador
 * é conferida no serviço, por ator. A permissão fina ("mudar parâmetro")
 * chegou na integração P1-B como {@code parameters:manage} (concessão
 * direta, nenhum perfil de fábrica — como {@code registry:export}): toda
 * rota desta classe a exige. A trava fina por papel de tenant (perfis por
 * função) fica para a P1.37, como as demais permissões diretas.
 */
@RestController
@RequestMapping("/api/v1/tenant/parameters")
@PreAuthorize("hasAuthority('parameters:manage')")
public class TenantParameterController {

    public record ProposeInput(String definitionKey, JsonNode value, LocalDate validFrom, LocalDate validTo) {
    }

    public record AcceptInput(String accepterName, String accepterRole, boolean riskAlertAcknowledged) {
    }

    public record CurrentValue(String value) {
    }

    public record OnboardingPanelView(PlatformConsoleService.OnboardingPanel panel, boolean productionReady) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;
    private final PlatformConsoleService console;

    public TenantParameterController(DataSource dataSource, TenantCatalog catalog,
                                     PlatformConsoleService console) {
        this.dataSource = dataSource;
        this.catalog = catalog;
        this.console = console;
    }

    private TenantParameterService service() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new TenantParameterService(dataSource, schema, tenantId);
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    @PostMapping("/propose")
    public TenantParameterService.ParameterVersionView propose(@RequestBody ProposeInput in,
                                                               JwtAuthenticationToken auth) {
        return service().propose(in.definitionKey(), in.value() == null ? "null" : in.value().toString(),
                in.validFrom(), in.validTo(), actorId(auth));
    }

    @PostMapping("/{versionId}/approve")
    public TenantParameterService.ParameterVersionView approve(@PathVariable UUID versionId,
                                                               JwtAuthenticationToken auth) {
        return service().approve(versionId, actorId(auth));
    }

    @PostMapping("/{versionId}/accept")
    public void accept(@PathVariable UUID versionId, @RequestBody AcceptInput in,
                       JwtAuthenticationToken auth) {
        service().accept(versionId, actorId(auth), in.accepterName(), in.accepterRole(),
                in.riskAlertAcknowledged());
    }

    @GetMapping("/{definitionKey}/change")
    public TenantParameterService.ParameterChangeView change(@PathVariable String definitionKey,
                                                             @RequestParam UUID proposedVersionId) {
        return service().changeView(definitionKey, proposedVersionId);
    }

    @GetMapping("/{definitionKey}/current")
    public CurrentValue current(@PathVariable String definitionKey) {
        return new CurrentValue(service().currentAcceptedValue(definitionKey));
    }

    @GetMapping("/onboarding")
    public OnboardingPanelView onboarding() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        return new OnboardingPanelView(console.onboardingPanel(tenantId), service().productionGate(console));
    }
}
