package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.security.auth.PermissionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.13: as autorizações do cadastro são permissões do catálogo, não papéis fixos, e o
 * formato antigo com ponto não aparece em nenhuma delas.
 */
class RegistryRolesTest {

    @Test
    @DisplayName("READ, WRITE, EXPORT, REVEAL_DOCUMENT e SEARCH_DOCUMENT são hasAuthority de permissões do catálogo")
    void rolesAreCatalogAuthorities() {
        assertThat(RegistryRoles.READ).isEqualTo("hasAuthority('registry:read')");
        assertThat(RegistryRoles.WRITE).isEqualTo("hasAuthority('registry:write')");
        assertThat(RegistryRoles.EXPORT).isEqualTo("hasAuthority('registry:export')");
        assertThat(RegistryRoles.REVEAL_DOCUMENT).isEqualTo("hasAuthority('registry:reveal-document')");
        assertThat(RegistryRoles.SEARCH_DOCUMENT).isEqualTo("hasAuthority('registry:search-document')");
        assertThat(PermissionCatalog.isPermission("registry:read")).isTrue();
        assertThat(PermissionCatalog.isPermission("registry:write")).isTrue();
        assertThat(PermissionCatalog.isPermission("registry:export")).isTrue();
        assertThat(PermissionCatalog.isPermission("registry:reveal-document")).isTrue();
        assertThat(PermissionCatalog.isPermission("registry:search-document")).isTrue();
        assertThat(RegistryRoles.READ + RegistryRoles.WRITE + RegistryRoles.EXPORT + RegistryRoles.REVEAL_DOCUMENT + RegistryRoles.SEARCH_DOCUMENT)
                .doesNotContain("hasAnyRole", "hasRole", "registry.reveal-document");
    }
}
