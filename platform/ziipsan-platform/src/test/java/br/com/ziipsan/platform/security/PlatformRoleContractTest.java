package br.com.ziipsan.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Permissoes de contrato SaaS (P2.12, ADR-0019): leitura para os quatro papeis, gestao e restricao so OWNER e FINANCE. */
class PlatformRoleContractTest {

    @Test
    @DisplayName("contract:read para todos; contract:manage e contract:restrict so OWNER e FINANCE")
    void contractPermissions() {
        assertThat(PlatformRole.permissionsFor("PLATFORM_OWNER"))
                .contains("contract:read", "contract:manage", "contract:restrict");
        assertThat(PlatformRole.permissionsFor("PLATFORM_FINANCE"))
                .contains("contract:read", "contract:manage", "contract:restrict");
        assertThat(PlatformRole.permissionsFor("PLATFORM_OPS")).contains("contract:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_OPS"))
                .doesNotContain("contract:manage", "contract:restrict");
        assertThat(PlatformRole.permissionsFor("PLATFORM_SUPPORT")).contains("contract:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_SUPPORT"))
                .doesNotContain("contract:manage", "contract:restrict");
        assertThat(PlatformRole.permissionsFor("ADMIN"))
                .doesNotContain("contract:read", "contract:manage", "contract:restrict");
        assertThat(PlatformRole.permissionsFor(null)).isEmpty();
    }
}
