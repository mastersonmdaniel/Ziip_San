package br.com.ziipsan.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.43: matriz de papéis de plataforma (CENTRAL-ZIIP §2) no código. */
class PlatformRoleTest {

    @Test
    @DisplayName("os quatro papéis do §2 existem, e só eles são papéis de plataforma")
    void fourRolesOnly() {
        assertThat(PlatformRole.ALL).containsExactlyInAnyOrder(
                "PLATFORM_OWNER", "PLATFORM_OPS", "PLATFORM_SUPPORT", "PLATFORM_FINANCE");
        assertThat(PlatformRole.isPlatformRole("PLATFORM_OWNER")).isTrue();
        assertThat(PlatformRole.isPlatformRole("ADMIN")).isFalse();
        assertThat(PlatformRole.isPlatformRole("VIEWER")).isFalse();
        assertThat(PlatformRole.isPlatformRole(null)).isFalse();
    }

    @Test
    @DisplayName("deployment:read: OWNER e OPS têm; SUPPORT e FINANCE, não (operação técnica, não de suporte nem de financeiro)")
    void deploymentReadMatrix() {
        assertThat(PlatformRole.permissionsFor("PLATFORM_OWNER")).contains("deployment:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_OPS")).contains("deployment:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_SUPPORT")).doesNotContain("deployment:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_FINANCE")).doesNotContain("deployment:read");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "OPERATOR", "VIEWER", "registry:reveal-document", "DESCONHECIDO"})
    @DisplayName("papel de tenant ou desconhecido não concede permissão de plataforma (negação por padrão)")
    void nonPlatformRoleGrantsNothing(String role) {
        assertThat(PlatformRole.permissionsFor(role)).isEmpty();
        assertThat(PermissionResolver.permissionsForRoles(java.util.List.of(role)))
                .doesNotContain("deployment:read");
    }

    @Test
    @DisplayName("console (P1.6): OWNER tem tudo; OPS gerencia sem suspender nem eliminar; SUPPORT só lê; FINANCE lê e suspende")
    void consoleMatrix() {
        assertThat(PlatformRole.permissionsFor("PLATFORM_OWNER"))
                .contains("console:read", "console:manage", "console:suspend", "console:delete");
        assertThat(PlatformRole.permissionsFor("PLATFORM_OPS"))
                .contains("console:read", "console:manage")
                .doesNotContain("console:suspend", "console:delete");
        assertThat(PlatformRole.permissionsFor("PLATFORM_SUPPORT"))
                .containsExactly("console:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_FINANCE"))
                .contains("console:read", "console:suspend")
                .doesNotContain("console:manage", "console:delete");
    }

    @Test
    @DisplayName("o conversor expande o papel de plataforma para a permissão do catálogo")
    void resolverExpandsPlatformRole() {
        assertThat(PermissionResolver.permissionsForRoles(java.util.List.of("PLATFORM_OPS")))
                .contains("deployment:read");
        assertThat(PermissionResolver.permissionsForRoles(java.util.List.of("PLATFORM_SUPPORT")))
                .doesNotContain("deployment:read");
    }
}
