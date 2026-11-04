package br.com.ziipsan.platform.vertical;

import br.com.ziipsan.security.auth.PermissionCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1.37: os perfis de fábrica declarados no manifesto do pacote
 * ({@code packages/sanitation/vertical.json}) são exatamente os do código
 * ({@code PermissionCatalog}), com a mesma versão, e o menu derivado do mapa do
 * produto (P0.47) para cada perfil é só o que a permissão do perfil permite ver.
 *
 * <p>Regra do menu: item visível ⇔ permissão do item ∈ permissões efetivas do perfil.
 * Para perfil desligado, a permissão efetiva é vazia (fail-closed até a ED-75), mesmo
 * com o conjunto declarado no catálogo. "Arquivos gerados" não é item de módulo do
 * manifesto (é ponto de entrega transversal da P1.12, com {@code registry:read}) e por
 * isso não aparece nesta derivação do manifesto — o teste do frontend cobre o menu
 * real, com ele.
 */
class FactoryProfilesManifestTest {

    private static VerticalManifest manifest() {
        return VerticalManifestLoader.load(
                VerticalManifestTest.repoRoot().resolve("packages/sanitation/vertical.json"));
    }

    private static List<VerticalManifest.MenuItem> allMenuItems(VerticalManifest manifest) {
        return manifest.modules().stream().flatMap(m -> m.menu().stream()).toList();
    }

    private static List<String> visibleRoutes(Set<String> effectivePermissions, VerticalManifest manifest) {
        return allMenuItems(manifest).stream()
                .filter(item -> item.permission() != null && effectivePermissions.contains(item.permission()))
                .map(VerticalManifest.MenuItem::route)
                .toList();
    }

    @Test
    void manifestProfilesMirrorCodeExactly() {
        VerticalManifest manifest = manifest();
        assertEquals(PermissionCatalog.VERSION, manifest.version(),
                "A versao dos perfis de fabrica e a versao do pacote");
        assertEquals(26, manifest.factoryProfiles().size());
        assertEquals(26, PermissionCatalog.factoryProfiles().size());
        for (PermissionCatalog.FactoryProfile codeProfile : PermissionCatalog.factoryProfiles()) {
            VerticalManifest.FactoryProfileDef declared = manifest.factoryProfiles().stream()
                    .filter(p -> p.code().equals(codeProfile.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("perfil ausente no manifesto: " + codeProfile.name()));
            assertEquals(codeProfile.label(), declared.label(), "rotulo: " + codeProfile.name());
            assertEquals(codeProfile.enabled(), declared.enabled(), "enabled: " + codeProfile.name());
            assertEquals(codeProfile.homePage(), declared.homePage(), "homePage: " + codeProfile.name());
            assertEquals(codeProfile.permissions(), new HashSet<>(declared.permissions()),
                    "permissoes: " + codeProfile.name());
        }
        Set<String> codes = manifest.factoryProfiles().stream()
                .map(VerticalManifest.FactoryProfileDef::code).collect(Collectors.toSet());
        assertEquals(26, codes.size(), "Codigos de perfil duplicados no manifesto");
    }

    @Test
    void menuDerivesFromProductMapPermissions() {
        VerticalManifest manifest = manifest();
        assertEquals(List.of("/cadastro/imoveis", "/cadastro/clientes", "/cadastro/territorio",
                "/cadastro/catalogos", "/gis/catalogo"),
                allMenuItems(manifest).stream().map(VerticalManifest.MenuItem::route).toList());
        assertEquals(allMenuItems(manifest).stream().map(VerticalManifest.MenuItem::route).toList(),
                visibleRoutes(PermissionCatalog.FactoryProfile.ADMIN.permissions(), manifest),
                "ADMIN ve todo o menu do mapa");
        assertEquals(List.of("/cadastro/imoveis", "/cadastro/clientes", "/cadastro/territorio",
                "/cadastro/catalogos"),
                visibleRoutes(PermissionCatalog.FactoryProfile.ATENDENTE.permissions(), manifest));
        assertEquals(List.of("/gis/catalogo"),
                visibleRoutes(PermissionCatalog.FactoryProfile.EDITOR_CAD_TECNICO.permissions(), manifest));
        assertEquals(List.of("/gis/catalogo"),
                visibleRoutes(PermissionCatalog.FactoryProfile.REVISOR_CAD_TECNICO.permissions(), manifest));
        assertTrue(visibleRoutes(PermissionCatalog.FactoryProfile.ALMOXARIFE.permissions(), manifest).isEmpty(),
                "Almoxarife nao tem permissao catalogada nesta branch e nao ve menu");
        assertTrue(visibleRoutes(PermissionCatalog.FactoryProfile.OPERADOR_ESTACAO.permissions(), manifest).isEmpty());
    }

    @Test
    void disabledProfilesSeeNoMenuEvenWithDeclaredPermissions() {
        VerticalManifest manifest = manifest();
        for (PermissionCatalog.FactoryProfile profile : PermissionCatalog.factoryProfiles()) {
            if (profile.enabled()) {
                continue;
            }
            assertFalse(profile.permissions().isEmpty(),
                    "Perfil desligado mantem o conjunto previsto: " + profile.name());
            assertTrue(PermissionCatalog.authoritiesForRole(profile.name()).isEmpty());
            assertTrue(visibleRoutes(PermissionCatalog.authoritiesForRole(profile.name()), manifest).isEmpty(),
                    "Perfil desligado nao ve menu ate a liberacao da ED-75: " + profile.name());
        }
    }
}
