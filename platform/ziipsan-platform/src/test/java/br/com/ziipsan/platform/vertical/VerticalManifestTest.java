package br.com.ziipsan.platform.vertical;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.security.auth.PermissionCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato do pacote de vertical (P0.47): o manifesto real carrega e valida, a certification
 * tem motor no núcleo, o menu declarado é o menu atual do frontend e as permissões do
 * manifesto são só as do catálogo da P0.13.
 */
class VerticalManifestTest {

    static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("packages/sanitation/vertical.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Raiz do repositorio nao encontrada a partir de user.dir");
    }

    @Test
    void sanitationManifestLoadsAndValidates() {
        VerticalManifest manifest = VerticalManifestLoader.load(
                repoRoot().resolve("packages/sanitation/vertical.json"));
        assertEquals("sanitation", manifest.id());
        assertEquals("0.1.0", manifest.version());
        assertTrue(manifest.modules().stream()
                .anyMatch(m -> "certification".equals(m.id()) && "core".equals(m.engine())),
                "O manifesto deve incluir certification com motor no nucleo");
        assertTrue(manifest.modules().stream()
                .anyMatch(m -> "registry".equals(m.id()) && "active".equals(m.status())));
    }

    @Test
    void registryMenuMatchesCurrentFrontendMenu() {
        VerticalManifest manifest = VerticalManifestLoader.load(
                repoRoot().resolve("packages/sanitation/vertical.json"));
        VerticalManifest.ModuleManifest registry = manifest.modules().stream()
                .filter(m -> "registry".equals(m.id())).findFirst().orElseThrow();
        List<String> routes = registry.menu().stream().map(VerticalManifest.MenuItem::route).toList();
        // Rotas de frontend/src/components/Layout.tsx e frontend/src/App.tsx nesta onda.
        assertEquals(List.of("/cadastro/imoveis", "/cadastro/clientes",
                "/cadastro/territorio", "/cadastro/catalogos"), routes);
        assertTrue(registry.apiRoutes().contains("/api/v1/registry/consumer-units"));
        assertTrue(registry.apiRoutes().contains("/api/v1/registry/customers"));
    }

    @Test
    void manifestPermissionsAreOnlyCataloguedOnes() {
        VerticalManifest manifest = VerticalManifestLoader.load(
                repoRoot().resolve("packages/sanitation/vertical.json"));
        // P1.37: o manifesto declara as permissões de tenant do catálogo (nunca as de
        // plataforma, que não pertencem ao pacote) e nada fora do catálogo.
        assertTrue(PermissionCatalog.allNames().containsAll(manifest.permissions()),
                "O manifesto nao pode inventar permissao fora do catalogo da P0.13");
        assertTrue(manifest.permissions().containsAll(
                List.of("registry:read", "registry:write", "registry:reveal-document",
                        "registry:search-document", "registry:export", "user:read", "user:write",
                        "gis:catalog-read", "gis:catalog-manage", "parameters:manage")));
        assertTrue(manifest.permissions().stream()
                .noneMatch(p -> p.startsWith("deployment:") || p.startsWith("console:")),
                "O pacote nao declara permissao de plataforma");
    }

    @Test
    void schemaFileDeclaresTheSameRequiredFields() throws IOException {
        String schema = Files.readString(
                repoRoot().resolve("packages/sanitation/vertical.schema.json"), StandardCharsets.UTF_8);
        JsonNode root = Json.readTree(schema);
        Set<String> required = new java.util.HashSet<>();
        root.path("required").forEach(node -> required.add(node.asText()));
        assertEquals(Set.of("id", "version", "minPlatformVersion", "nameKey",
                "descriptionKey", "modules", "gis"), required);
    }

    @Test
    void validatorRejectsDuplicateRouteAndMissingCertification() {
        VerticalManifest.ModuleManifest a = new VerticalManifest.ModuleManifest(
                "a", "A", "x/a", "active", "P0", null, null,
                List.of(new VerticalManifest.MenuItem("A", "/x", "i", null)),
                List.of(), List.of());
        VerticalManifest.ModuleManifest b = new VerticalManifest.ModuleManifest(
                "b", "B", "x/b", "declared", "P0", null, null,
                List.of(new VerticalManifest.MenuItem("B", "/x", "i", null)),
                List.of(), List.of());
        VerticalManifest noCert = new VerticalManifest(
                "sanitation", "0.1.0", "1.0.0-SNAPSHOT", "k", "d", List.of(a, b), List.of(), List.of(), List.of(),
                List.of());
        assertThrows(IllegalStateException.class, () -> VerticalManifestValidator.validate(noCert));
    }

    @Test
    void registryArchiveRequiresConfirmationAndHidesModules() {
        VerticalManifest manifest = VerticalManifestLoader.load(
                repoRoot().resolve("packages/sanitation/vertical.json"));
        VerticalPackageRegistry registry = new VerticalPackageRegistry();
        registry.register(manifest);
        assertEquals(VerticalPackageRegistry.ActivationState.ACTIVE, registry.state("sanitation"));
        assertThrows(IllegalStateException.class, () -> registry.archive("sanitation", false));
        registry.archive("sanitation", true);
        assertEquals(VerticalPackageRegistry.ActivationState.ARCHIVED, registry.state("sanitation"));
        assertTrue(registry.activeModules("sanitation").isEmpty());
    }

    @Test
    void productMapCoversEveryModuleAndMenuRoute() throws IOException {
        VerticalManifest manifest = VerticalManifestLoader.load(
                repoRoot().resolve("packages/sanitation/vertical.json"));
        String map = Files.readString(
                repoRoot().resolve("docs/arquitetura/product-map.md"), StandardCharsets.UTF_8);
        Set<String> markers = new java.util.HashSet<>();
        for (String line : map.split("\n")) {
            if (line.contains("<!-- module: ")) {
                markers.add(line.substring(line.indexOf("<!-- module: ") + 13, line.indexOf(" -->")).trim());
            }
        }
        Set<String> moduleIds = new java.util.HashSet<>();
        manifest.modules().forEach(m -> moduleIds.add(m.id()));
        assertEquals(moduleIds, markers, "O mapa do produto deve cobrir exatamente os modulos do manifesto");
        manifest.modules().forEach(module -> module.menu().forEach(item ->
                assertTrue(map.contains(item.route()),
                        "O mapa deve conter a rota de menu: " + item.route())));
    }
}
