package br.com.ziipsan.platform.vertical;

import java.util.HashSet;
import java.util.Set;

/**
 * Validação do manifesto no CI: implementa as regras de
 * {@code packages/sanitation/vertical.schema.json} em Java (o schema é o contrato; este
 * validador é a execução dele no build, sem depender de biblioteca externa de JSON Schema).
 *
 * <p>Regras: id e versão no formato do schema; módulos não vazios, com ids únicos; rotas
 * (menu e API) únicas no pacote; permissões no formato {@code recurso:ação} do catálogo da
 * P0.13; {@code certification} presente com motor no núcleo ({@code engine=core}, ADR-0041);
 * domínios do GIS com chaves únicas e ordem positiva.
 */
public final class VerticalManifestValidator {

    private VerticalManifestValidator() {
    }

    public static void validate(VerticalManifest manifest) {
        if (manifest == null) {
            throw new IllegalStateException("O manifesto e obrigatorio");
        }
        require(manifest.id() != null && manifest.id().matches("[a-z][a-z0-9-]*"),
                "O id do pacote deve seguir ^[a-z][a-z0-9-]*$: " + manifest.id());
        require(manifest.version() != null && manifest.version().matches("\\d+\\.\\d+\\.\\d+"),
                "A versao do pacote deve ser semantica: " + manifest.version());
        require(manifest.minPlatformVersion() != null && !manifest.minPlatformVersion().isBlank(),
                "A versao minima da plataforma e obrigatoria");
        require(manifest.modules() != null && !manifest.modules().isEmpty(),
                "O manifesto deve declarar ao menos um modulo");
        Set<String> moduleIds = new HashSet<>();
        Set<String> routes = new HashSet<>();
        boolean certificationWithCoreEngine = false;
        for (VerticalManifest.ModuleManifest module : manifest.modules()) {
            require(module.id() != null && module.id().matches("[a-z][a-z0-9-]*"),
                    "O id do modulo deve seguir ^[a-z][a-z0-9-]*$: " + module.id());
            require(moduleIds.add(module.id()), "Modulo duplicado no manifesto: " + module.id());
            require(module.name() != null && !module.name().isBlank(),
                    "O nome do modulo e obrigatorio: " + module.id());
            require(module.mavenModule() != null && !module.mavenModule().isBlank(),
                    "O modulo Maven do modulo e obrigatorio: " + module.id());
            require(module.status() != null && Set.of("active", "declared", "skeleton").contains(module.status()),
                    "Status invalido no modulo " + module.id() + ": " + module.status());
            require(module.originTask() != null && !module.originTask().isBlank(),
                    "A tarefa de origem do modulo e obrigatoria: " + module.id());
            checkPermission(module.permission(), module.id());
            for (VerticalManifest.MenuItem item : module.menu()) {
                require(item.route() != null && item.route().startsWith("/"),
                        "A rota de menu deve comecar com /: " + module.id());
                require(routes.add("menu:" + item.route()),
                        "Rota de menu duplicada no pacote: " + item.route());
                checkPermission(item.permission(), module.id());
            }
            for (String route : module.apiRoutes()) {
                require(route.startsWith("/"), "A rota de API deve comecar com /: " + route);
                require(routes.add("api:" + route), "Rota de API duplicada no pacote: " + route);
            }
            if ("certification".equals(module.id()) && "core".equals(module.engine())) {
                certificationWithCoreEngine = true;
            }
        }
        require(certificationWithCoreEngine,
                "O manifesto deve incluir certification com motor no nucleo (engine=core, ADR-0041)");
        Set<String> domainKeys = new HashSet<>();
        for (VerticalManifest.GisDomain domain : manifest.gisDomains()) {
            require(domain.key() != null && domain.key().matches("[A-Z_]+"),
                    "A chave do dominio GIS deve ser maiuscula: " + domain.key());
            require(domainKeys.add(domain.key()), "Dominio GIS duplicado: " + domain.key());
            require(domain.order() >= 1, "A ordem do dominio GIS deve ser positiva: " + domain.key());
        }
        for (String permission : manifest.permissions()) {
            checkPermission(permission, "pacote");
        }
    }

    private static void checkPermission(String permission, String owner) {
        if (permission == null) {
            return; // Sem permissao catalogada: declarado como null, nunca inventado.
        }
        require(permission.matches("[a-z]+:[a-z-]+"),
                "Permissao fora do formato recurso:acao em " + owner + ": " + permission);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Manifesto invalido: " + message);
        }
    }
}
