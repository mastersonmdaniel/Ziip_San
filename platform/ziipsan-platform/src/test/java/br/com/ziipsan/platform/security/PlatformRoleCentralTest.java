package br.com.ziipsan.platform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Permissoes da Central (P1.17): leitura para os quatro papeis, gestao so OWNER/OPS. */
class PlatformRoleCentralTest {

    @Test
    @DisplayName("central:read para todos; central:manage so OWNER e OPS")
    void centralPermissions() {
        assertThat(PlatformRole.permissionsFor("PLATFORM_OWNER")).contains("central:read", "central:manage");
        assertThat(PlatformRole.permissionsFor("PLATFORM_OPS")).contains("central:read", "central:manage");
        assertThat(PlatformRole.permissionsFor("PLATFORM_SUPPORT")).contains("central:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_SUPPORT")).doesNotContain("central:manage");
        assertThat(PlatformRole.permissionsFor("PLATFORM_FINANCE")).contains("central:read");
        assertThat(PlatformRole.permissionsFor("PLATFORM_FINANCE")).doesNotContain("central:manage");
        assertThat(PlatformRole.permissionsFor("ADMIN")).doesNotContain("central:read", "central:manage");
        assertThat(PlatformRole.permissionsFor(null)).isEmpty();
    }
}
