package br.com.ziipsan.reporting.bi;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-47: SSO com o Keycloak (emissor de ambiente, realm do projeto,
 * cliente criado na implantação — nunca presumido) e marca do produto
 * vinda do pacote da P0.34, sem cor nem logotipo inventados aqui.
 */
class SupersetSsoBrandingTest {

    @Test
    void descobertaOidcDoRealmDoProjeto() {
        SupersetSsoConfig config = new SupersetSsoConfig(
                URI.create("https://sso.example.invalid"), SupersetSsoConfig.DEFAULT_REALM,
                "superset-bi", URI.create("https://bi.example.invalid/oauth-authorized/keycloak"));
        assertThat(config.realmIssuer()).isEqualTo("https://sso.example.invalid/realms/ziipsan");
        assertThat(config.discoveryUrl())
                .isEqualTo("https://sso.example.invalid/realms/ziipsan/.well-known/openid-configuration");
    }

    @Test
    void emissorSemTlsForaDeLoopbackEhRecusado() {
        assertThatThrownBy(() -> new SupersetSsoConfig(
                URI.create("http://sso.example.invalid"), "ziipsan", "superset-bi",
                URI.create("https://bi.example.invalid/cb")))
                .isInstanceOf(IllegalArgumentException.class);
        // O ambiente local (P0.27/P0.29) roda em loopback: aceito.
        SupersetSsoConfig local = new SupersetSsoConfig(
                URI.create("http://localhost:8080"), "ziipsan", "superset-bi",
                URI.create("http://localhost:8088/oauth-authorized/keycloak"));
        assertThat(local.realmIssuer()).isEqualTo("http://localhost:8080/realms/ziipsan");
    }

    @Test
    void clienteOidcNaoEhPresumido() {
        assertThatThrownBy(() -> new SupersetSsoConfig(
                URI.create("https://sso.example.invalid"), "ziipsan", " ",
                URI.create("https://bi.example.invalid/cb")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientId");
    }

    @Test
    void marcaExigeNomeDoProdutoEAtivosDaP034() {
        SupersetBranding branding = new SupersetBranding("ZiipSan", "/brand/logo.svg", "#123456");
        assertThat(branding.appName()).isEqualTo("ZiipSan");
        assertThatThrownBy(() -> new SupersetBranding("Apache Superset", "/brand/logo.svg", "#123456"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupersetBranding("ZiipSan", " ", "#123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P0.34");
        assertThatThrownBy(() -> new SupersetBranding("ZiipSan", "/brand/logo.svg", "azul"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
