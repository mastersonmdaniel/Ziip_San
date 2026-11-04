package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.domain.HostResolver.Kind;
import br.com.ziipsan.platform.domain.HostResolver.Resolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2.27 — Origin, redirect_uri do OIDC e cookie __Host- mapeados por
 * host: só o host resolvido gera origem e redirect; host não resolvido
 * nunca vira origem, e o cookie fica preso ao host exato (sem Domain).
 */
class HostSecurityPolicyTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Resolution RESOLVED =
            new Resolution(Kind.CUSTOM_DOMAIN, TENANT, "app.cliente.example");
    private static final Resolution NONE = Resolution.none();

    @Test
    @DisplayName("host resolvido: origem https, redirect_uri no host e cookie preso ao host")
    void resolvedHostMaps() {
        assertThat(HostSecurityPolicy.trustedOrigin("APP.Cliente.EXAMPLE:8443", RESOLVED))
                .contains("https://app.cliente.example");
        assertThat(HostSecurityPolicy.trustedOrigins("app.cliente.example", RESOLVED))
                .containsExactly("https://app.cliente.example");
        assertThat(HostSecurityPolicy.loginRedirectUri("app.cliente.example", RESOLVED, "/bff/login"))
                .contains("https://app.cliente.example/bff/login");
        assertThat(HostSecurityPolicy.sessionCookieHost("app.cliente.example", RESOLVED))
                .contains("app.cliente.example");
    }

    @Test
    @DisplayName("host não resolvido: nenhuma origem, nenhum redirect, nenhum cookie")
    void unresolvedHostMapsToNothing() {
        assertThat(HostSecurityPolicy.trustedOrigin("outro.example", NONE)).isEmpty();
        assertThat(HostSecurityPolicy.loginRedirectUri("outro.example", NONE, "/bff/login")).isEmpty();
        assertThat(HostSecurityPolicy.sessionCookieHost("outro.example", NONE)).isEmpty();
    }

    @Test
    @DisplayName("redirect_uri recusa caminho relativo e // inicial (open redirect)")
    void redirectUriRejectsBadPaths() {
        assertThat(HostSecurityPolicy.loginRedirectUri("app.cliente.example", RESOLVED, "bff/login")).isEmpty();
        assertThat(HostSecurityPolicy.loginRedirectUri("app.cliente.example", RESOLVED, "//evil.example/x"))
                .isEmpty();
        assertThat(HostSecurityPolicy.loginRedirectUri("app.cliente.example", RESOLVED, null)).isEmpty();
    }

    @Test
    @DisplayName("unquote do TXT: aspas removidas e pedaços concatenados")
    void txtUnquote() {
        assertThat(JndiDns.unquote("\"ziipsan-verify=abc\"")).isEqualTo("ziipsan-verify=abc");
        assertThat(JndiDns.unquote("\"ziipsan-\"\"verify=abc\"")).isEqualTo("ziipsan-verify=abc");
        assertThat(JndiDns.unquote("\"ziipsan-\" \"verify=abc\"")).isEqualTo("ziipsan-verify=abc");
        assertThat(JndiDns.unquote("ziipsan-verify=abc")).isEqualTo("ziipsan-verify=abc");
    }
}
