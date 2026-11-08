package br.com.ziipsan.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-06 no catálogo da P0.13: as três permissões novas existem, estão em
 * perfil de fábrica (a regra de cobertura do CI não as deixa órfãs) e só nos
 * perfis que o aceite implica — gestão e recertificação no ADMIN; leitura
 * da trilha no ADMIN e no AUDITOR_INTERNO.
 */
class PermissionCatalogEd06Test {

    @Test
    @DisplayName("as permissões da ED-06 estão no catálogo, no formato recurso:ação")
    void ed06PermissionsExist() {
        assertThat(PermissionCatalog.allNames()).contains(
                "access:manage-profiles", "access:recertify", "access:audit-read");
        assertThat(PermissionCatalog.isPermission("access:manage-profiles")).isTrue();
        assertThat(PermissionCatalog.isPermission("access:audit-read")).isTrue();
    }

    @Test
    @DisplayName("cobertura: nenhuma permissão da ED-06 fica fora de perfil nem de exceção")
    void coverage() {
        Set<String> covered = new java.util.HashSet<>(PermissionCatalog.coverageExemptions().keySet());
        for (PermissionCatalog.FactoryProfile profile : PermissionCatalog.factoryProfiles()) {
            covered.addAll(profile.permissions());
        }
        // Escopo desta tarefa: só as permissões da ED-06 (a cobertura global
        // é do PermissionCatalogTest do projeto, que reconcilia as frentes).
        assertThat(covered).contains(
                "access:manage-profiles", "access:recertify", "access:audit-read");
    }

    @Test
    @DisplayName("atribuição por perfil: ADMIN gere e recertifica; auditor interno lê a trilha")
    void profileAssignments() {
        assertThat(PermissionCatalog.profilesIncluding("access:manage-profiles"))
                .containsExactly("ADMIN");
        assertThat(PermissionCatalog.profilesIncluding("access:recertify"))
                .containsExactly("ADMIN");
        assertThat(PermissionCatalog.profilesIncluding("access:audit-read"))
                .containsExactlyInAnyOrder("ADMIN", "AUDITOR_INTERNO");
        assertThat(PermissionCatalog.authoritiesForRole("VIEWER"))
                .doesNotContain("access:manage-profiles", "access:recertify", "access:audit-read");
    }
}
