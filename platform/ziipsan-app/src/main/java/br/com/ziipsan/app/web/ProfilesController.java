package br.com.ziipsan.app.web;

import br.com.ziipsan.security.auth.PermissionCatalog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Perfis de fábrica do produto (P1.37), para o administrador do tenant montar a
 * atribuição e para o frontend conhecer os perfis sem duplicar o catálogo.
 *
 * <p>Leitura exige {@code user:read}, a mesma da gestão de usuários: quem não pode ver
 * usuários não precisa ver os perfis atribuíveis. O corpo é dado estático do catálogo
 * versionado ({@code PermissionCatalog.VERSION}, a versão do pacote sanitation) — não
 * há tabela própria de perfis, e perfis compostos pelo administrador do tenant ficam
 * na ED-06, sobre este catálogo. Auditor externo e regulador vêm com
 * {@code enabled=false}: existem, mas a atribuição é recusada até a liberação da ED-75.
 */
@RestController
public class ProfilesController {

    public record FactoryProfileView(String code, String label, String homePage, boolean enabled,
                                     List<String> permissions) {
    }

    public record FactoryProfilesView(String version, List<FactoryProfileView> profiles) {
    }

    @GetMapping("/api/v1/profiles")
    @PreAuthorize("hasAuthority('user:read')")
    public FactoryProfilesView profiles() {
        List<FactoryProfileView> views = PermissionCatalog.factoryProfiles().stream()
                .map(p -> new FactoryProfileView(p.name(), p.label(), p.homePage(), p.enabled(),
                        p.permissions().stream().sorted().toList()))
                .toList();
        return new FactoryProfilesView(PermissionCatalog.VERSION, views);
    }
}
