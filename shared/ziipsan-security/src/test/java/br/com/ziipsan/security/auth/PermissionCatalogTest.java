package br.com.ziipsan.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catálogo P0.13 + perfis de fábrica P1.37: formato único {@code recurso:ação}, sem
 * duplicata, perfis dentro do catálogo, ADMIN com o conjunto de administração do tenant,
 * negação por padrão, e a regra da P1.37 — permissão do catálogo que não está em nenhum
 * perfil de fábrica nem na lista explícita de exceções falha o build.
 */
class PermissionCatalogTest {

    @Test
    @DisplayName("todo nome do catálogo está no formato recurso:ação, sem ponto, sem duplicata")
    void namesAreResourceAction() {
        Set<String> seen = new HashSet<>();
        for (PermissionCatalog.Permission p : PermissionCatalog.ALL) {
            assertThat(p.name()).matches("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");
            assertThat(seen.add(p.name())).as("permissão duplicada: %s", p.name()).isTrue();
            assertThat(p.name()).isEqualTo(p.resource() + ":" + p.action());
            assertThat(p.originTask()).isNotBlank();
            assertThat(p.module()).isNotBlank();
        }
    }

    @Test
    @DisplayName("todo perfil é subconjunto do catálogo; ADMIN tem o conjunto de administração do tenant")
    void profilesAreSubsetsAndAdminHasAllButReveal() {
        Set<String> all = PermissionCatalog.allNames();
        for (PermissionCatalog.FactoryProfile profile : PermissionCatalog.FactoryProfile.values()) {
            assertThat(all).containsAll(profile.permissions());
        }
        // P1.37: ADMIN administra o tenant (cadastro, usuários, catálogo GIS, parâmetros).
        // parameters:manage deixa de ser concessão direta e entra no ADMIN e no GESTOR.
        // Integração P1-C: a marca do tenant (P1.19) e os chamados de suporte (P1.23)
        // entram no ADMIN; a busca global e suas leituras (P1.33) continuam fora de
        // qualquer perfil, por decisão explícita da frente P1.33 (concessão direta).
        assertThat(PermissionCatalog.FactoryProfile.ADMIN.permissions())
                .containsExactlyInAnyOrder("registry:read", "registry:write", "user:read", "user:write",
                        "gis:catalog-read", "gis:catalog-manage", "parameters:manage",
                        "brand:manage", "support:read", "support:write", "support:manage");
        assertThat(PermissionCatalog.FactoryProfile.ADMIN.permissions())
                .doesNotContain("registry:reveal-document");
        // P0.11: buscar por documento também é concessão direta, em nenhum perfil de fábrica.
        assertThat(PermissionCatalog.FactoryProfile.ADMIN.permissions())
                .doesNotContain("registry:search-document");
        // P1.33: busca global e leituras de OS, conta e documento também são
        // concessão direta, em nenhum perfil de fábrica (perfis por função: P1.37).
        assertThat(PermissionCatalog.FactoryProfile.ADMIN.permissions())
                .doesNotContain("search:read", "service-order:read", "billing:read", "document:read");
        assertThat(PermissionCatalog.isPermission("search:read")).isTrue();
        assertThat(PermissionCatalog.isPermission("service-order:read")).isTrue();
        assertThat(PermissionCatalog.isPermission("billing:read")).isTrue();
        assertThat(PermissionCatalog.isPermission("document:read")).isTrue();
        assertThat(PermissionCatalog.authoritiesForRole("search:read")).containsExactly("search:read");
        assertThat(PermissionCatalog.authoritiesForRole("registry:search-document"))
                .containsExactly("registry:search-document");
    }

    @Test
    @DisplayName("papel desconhecido não concede nada; formato antigo com ponto não é permissão")
    void denyByDefault() {
        assertThat(PermissionCatalog.authoritiesForRole("SUPERUSER")).isEmpty();
        assertThat(PermissionCatalog.authoritiesForRole(null)).isEmpty();
        assertThat(PermissionCatalog.isPermission("registry.reveal-document")).isFalse();
        assertThat(PermissionCatalog.isPermission("gis.edit")).isFalse();
    }

    @Test
    @DisplayName("perfil expande para suas permissões; permissão direta vale por si só")
    void roleExpansion() {
        assertThat(PermissionCatalog.authoritiesForRole("VIEWER")).containsExactly("registry:read");
        assertThat(PermissionCatalog.authoritiesForRole("OPERATOR"))
                .containsExactlyInAnyOrder("registry:read", "registry:write");
        assertThat(PermissionCatalog.authoritiesForRole("registry:reveal-document"))
                .containsExactly("registry:reveal-document");
    }

    @Test
    @DisplayName("P1.37: os 26 perfis de fábrica existem, com código curto, rótulo e página inicial")
    void factoryProfilesAreComplete() {
        List<PermissionCatalog.FactoryProfile> profiles = PermissionCatalog.factoryProfiles();
        assertThat(profiles).hasSize(26);
        Set<String> codes = new HashSet<>();
        for (PermissionCatalog.FactoryProfile profile : profiles) {
            assertThat(codes.add(profile.name())).as("código duplicado: %s", profile.name()).isTrue();
            assertThat(profile.name()).matches("^[A-Z][A-Z0-9_]*$");
            // Limite da coluna user_profiles.profile da V1_9.
            assertThat(profile.name().length()).as("código acima de 20 caracteres: %s", profile.name())
                    .isLessThanOrEqualTo(20);
            assertThat(profile.label()).isNotBlank();
            assertThat(profile.homePage()).isEqualTo(PermissionCatalog.HOME_PAGE);
        }
        assertThat(profiles.stream().map(Enum::name).toList()).containsExactly(
                "ADMIN", "MANAGER", "OPERATOR", "VIEWER",
                "ATENDENTE", "SUPERVISOR_COMERCIAL", "FATURISTA", "ANALISTA_ARRECADACAO",
                "LEITURISTA", "PROGRAMADOR_OS", "EXECUTOR_CAMPO", "ALMOXARIFE",
                "OPERADOR_ESTACAO", "ANALISTA_PERDAS", "LABORATORISTA", "RESP_TEC_QUALIDADE",
                "EDITOR_CAD_TECNICO", "REVISOR_CAD_TECNICO", "GESTOR", "AUDITOR_INTERNO",
                "OUVIDOR", "ENCARREGADO_DADOS", "PROCURADOR", "CONTADOR",
                "AUDITOR_EXTERNO", "REGULADOR");
        assertThat(PermissionCatalog.VERSION).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("P1.37: auditor externo e regulador nascem desligados e não expandem para nada")
    void disabledProfilesExpandToNothing() {
        assertThat(PermissionCatalog.FactoryProfile.AUDITOR_EXTERNO.enabled()).isFalse();
        assertThat(PermissionCatalog.FactoryProfile.REGULADOR.enabled()).isFalse();
        assertThat(PermissionCatalog.authoritiesForRole("AUDITOR_EXTERNO")).isEmpty();
        assertThat(PermissionCatalog.authoritiesForRole("REGULADOR")).isEmpty();
        // O conjunto declarado existe (é o previsto para a liberação da ED-75),
        // mas não concede nada enquanto desligado.
        assertThat(PermissionCatalog.FactoryProfile.AUDITOR_EXTERNO.permissions()).isNotEmpty();
        assertThat(PermissionCatalog.FactoryProfile.REGULADOR.permissions()).isNotEmpty();
        assertThat(PermissionCatalog.factoryProfiles().stream()
                .filter(p -> !p.enabled()).map(Enum::name).toList())
                .containsExactly("AUDITOR_EXTERNO", "REGULADOR");
    }

    @Test
    @DisplayName("P1.37: parameters:manage está só no ADMIN e no GESTOR; o resto continua 403 sem ela")
    void parametersManageIsInAdminAndManagerProfiles() {
        assertThat(PermissionCatalog.profilesIncluding("parameters:manage"))
                .containsExactly("ADMIN", "GESTOR");
        assertThat(PermissionCatalog.authoritiesForRole("ADMIN")).contains("parameters:manage");
        assertThat(PermissionCatalog.authoritiesForRole("GESTOR")).contains("parameters:manage");
        assertThat(PermissionCatalog.authoritiesForRole("MANAGER")).doesNotContain("parameters:manage");
        assertThat(PermissionCatalog.authoritiesForRole("ATENDENTE")).doesNotContain("parameters:manage");
    }

    @Test
    @DisplayName("P1.37: perfis sem permissão catalogada são só os de domínio ainda sem catálogo")
    void emptyProfilesAreOnlyPendingDomainOnes() {
        // Integração P1-C: o domínio de qualidade foi catalogado (P1.32), então
        // LABORATORISTA e RESP_TEC_QUALIDADE deixam a lista; estoque e estação
        // continuam sem domínio catalogado.
        assertThat(PermissionCatalog.factoryProfiles().stream()
                .filter(p -> p.permissions().isEmpty()).map(Enum::name).toList())
                .containsExactly("ALMOXARIFE", "OPERADOR_ESTACAO");
    }

    @Test
    @DisplayName("P1.37: toda permissão está em algum perfil ou na lista explícita de exceções")
    void everyPermissionIsInAProfileOrAnExplicitExemption() {
        Set<String> exempt = PermissionCatalog.coverageExemptions().keySet();
        // Integração P1-C: entram as permissões de papel de plataforma da Central
        // (P1.17) e da fila de suporte (P1.23), e as de concessão direta da busca
        // global (P1.33). A lista continua exata: exceção nova exige justificativa.
        assertThat(exempt).containsExactlyInAnyOrder(
                "registry:reveal-document", "registry:search-document", "registry:export",
                "deployment:read", "console:read", "console:manage", "console:suspend", "console:delete",
                "central:read", "central:manage", "support:queue",
                "search:read", "service-order:read", "billing:read", "document:read");
        for (String permission : PermissionCatalog.allNames()) {
            boolean inProfile = !PermissionCatalog.profilesIncluding(permission).isEmpty();
            assertThat(inProfile || exempt.contains(permission))
                    .as("permissão sem perfil de fábrica e sem exceção explícita: %s", permission)
                    .isTrue();
        }
        for (String permission : exempt) {
            assertThat(PermissionCatalog.isPermission(permission))
                    .as("exceção fora do catálogo: %s", permission).isTrue();
            assertThat(PermissionCatalog.coverageExemptions().get(permission)).isNotBlank();
        }
    }

    @Test
    @DisplayName("P1.37: o mapa inverso perfil-por-permissão bate com os conjuntos declarados")
    void profilesIncludingMatchesDeclaredSets() {
        assertThat(PermissionCatalog.profilesIncluding("registry:read"))
                .contains("ADMIN", "MANAGER", "OPERATOR", "VIEWER", "ATENDENTE", "FATURISTA");
        assertThat(PermissionCatalog.profilesIncluding("gis:catalog-manage"))
                .containsExactly("ADMIN", "EDITOR_CAD_TECNICO");
        assertThat(PermissionCatalog.profilesIncluding("user:write"))
                .containsExactly("ADMIN");
        assertThat(PermissionCatalog.profilesIncluding("registry:reveal-document")).isEmpty();
    }
}
