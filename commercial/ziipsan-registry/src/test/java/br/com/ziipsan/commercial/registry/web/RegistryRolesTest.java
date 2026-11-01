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
    @DisplayName("READ, WRITE e REVEAL_DOCUMENT são hasAuthority de permissões do catálogo")
    void rolesAreCatalogAuthorities() {
        assertThat(RegistryRoles.READ).isEqualTo("hasAuthority('registry:read')");
        assertThat(RegistryRoles.WRITE).isEqualTo("hasAuthority('registry:write')");
        assertThat(RegistryRoles.REVEAL_DOCUMENT).isEqualTo("hasAuthority('registry:reveal-document')");
        assertThat(PermissionCatalog.isPermission("registry:read")).isTrue();
        assertThat(PermissionCatalog.isPermission("registry:write")).isTrue();
        assertThat(PermissionCatalog.isPermission("registry:reveal-document")).isTrue();
        assertThat(RegistryRoles.READ + RegistryRoles.WRITE + RegistryRoles.REVEAL_DOCUMENT)
                .doesNotContain("hasAnyRole", "hasRole", "registry.reveal-document");
    }
}
