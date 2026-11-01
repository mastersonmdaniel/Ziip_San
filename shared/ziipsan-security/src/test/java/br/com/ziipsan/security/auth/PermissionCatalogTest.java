package br.com.ziipsan.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catálogo P0.13: formato único {@code recurso:ação}, sem duplicata, perfis dentro do
 * catálogo, ADMIN com tudo e negação por padrão.
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
    @DisplayName("ADMIN tem todas as permissões; todo perfil é subconjunto do catálogo")
    void profilesAreSubsetsAndAdminHasAll() {
        Set<String> all = PermissionCatalog.allNames();
        for (PermissionCatalog.FactoryProfile profile : PermissionCatalog.FactoryProfile.values()) {
            assertThat(all).containsAll(profile.permissions());
        }
        assertThat(PermissionCatalog.FactoryProfile.ADMIN.permissions()).isEqualTo(all);
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
}
