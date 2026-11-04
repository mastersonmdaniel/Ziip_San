package br.com.ziipsan.platform.vertical;

import java.util.List;

/**
 * Manifesto de um pacote de vertical ({@code packages/<id>/vertical.json},
 * PLATAFORMA-E-VERTICAIS §3). Registro novo da P0.47, no lugar do
 * {@code ModuleRegistry} apagado na P0.25.
 *
 * <p>Semântica honesta dos campos: {@code status} distingue o que existe de verdade
 * ({@code active}: rota e menu no produto) do que está só declarado ({@code declared}:
 * módulo existe mas sem rota/menu até esta onda; {@code skeleton}: esqueleto, caso do
 * fiscal da P0.32). {@code permission} nulo significa "sem permissão catalogada na P0.13",
 * nunca uma permissão inventada. Domínios do GIS são declaração até a P1.26 (V13_1).
 */
public record VerticalManifest(
        String id,
        String version,
        String minPlatformVersion,
        String nameKey,
        String descriptionKey,
        List<ModuleManifest> modules,
        List<GisDomain> gisDomains,
        List<String> permissions,
        List<OnboardingStep> onboardingSteps,
        List<FactoryProfileDef> factoryProfiles) {

    /** Um passo do checklist de implantação do pacote (P1.6, ADR-0037). */
    public record OnboardingStep(String key, String title) {
    }

    /**
     * Um perfil de fábrica da P1.37 declarado no manifesto, espelhado de
     * {@code PermissionCatalog} (a fonte única é o código; o teste de manifesto confere
     * os dois lados). {@code enabled=false} só para auditor externo e regulador,
     * desligados até a liberação da ED-75.
     */
    public record FactoryProfileDef(
            String code,
            String label,
            String homePage,
            boolean enabled,
            List<String> permissions) {
    }

    /** Um módulo de negócio habilitável do pacote. */
    public record ModuleManifest(
            String id,
            String name,
            String mavenModule,
            String status,
            String originTask,
            String permission,
            String engine,
            List<MenuItem> menu,
            List<String> apiRoutes,
            List<String> unitSheetTabs) {
    }

    /** Um item de menu do módulo (rótulo, rota, ícone, permissão exigida). */
    public record MenuItem(String label, String route, String icon, String permission) {
    }

    /** Um domínio do GIS declarado pelo pacote. */
    public record GisDomain(String key, String label, String color, int order) {
    }
}
