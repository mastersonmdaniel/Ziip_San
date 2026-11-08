package br.com.ziipsan.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.30 no catálogo da P0.13 (DAD-31): as três permissões nomeadas pelo
 * aceite existem, com recurso/ação decompostos e tarefa de origem, e são
 * concessão direta (exceção de cobertura) — nenhum perfil de fábrica as
 * carrega, porque as três são desvios guardados, não rotina de função.
 */
class PermissionCatalogP330Test {

    @Test
    @DisplayName("as três permissões da P3.30 estão no catálogo, no formato recurso:ação")
    void p330PermissionsCatalogued() {
        assertThat(PermissionCatalog.isPermission("protocol:transfer-any-unit")).isTrue();
        assertThat(PermissionCatalog.isPermission("so:close-retroactive")).isTrue();
        assertThat(PermissionCatalog.isPermission("so:close-without-activities")).isTrue();
        for (PermissionCatalog.Permission permission : PermissionCatalog.ALL) {
            if (permission.name().startsWith("so:") || permission.name().equals("protocol:transfer-any-unit")) {
                assertThat(permission.originTask()).isEqualTo("P3.30");
                assertThat(permission.name()).isEqualTo(permission.resource() + ":" + permission.action());
            }
        }
    }

    @Test
    @DisplayName("as três são concessão direta: estão nas exceções de cobertura e em NENHUM perfil de fábrica")
    void p330PermissionsAreDirectGrants() {
        for (String name : new String[]{"protocol:transfer-any-unit", "so:close-retroactive",
                "so:close-without-activities"}) {
            assertThat(PermissionCatalog.coverageExemptions()).containsKey(name);
            assertThat(PermissionCatalog.profilesIncluding(name)).isEmpty();
            assertThat(PermissionCatalog.authoritiesForRole(name)).containsExactly(name);
        }
        // E nenhum perfil de fábrica as expande pela porta dos perfis.
        assertThat(PermissionCatalog.authoritiesForRole("ADMIN"))
                .doesNotContain("protocol:transfer-any-unit", "so:close-retroactive",
                        "so:close-without-activities");
    }
}
