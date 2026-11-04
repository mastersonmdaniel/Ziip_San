package br.com.ziipsan.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.26: proteção contra SSRF — só HTTPS, sem credencial embutida, sem
 * endereço privado/reservado, com a resolução de DNS do momento do envio.
 */
class SsrfUrlValidatorTest {

    private static SsrfUrlValidator.DnsResolver fixed(String host, String... ips) {
        return h -> {
            if (!h.equals(host)) {
                throw new UnknownHostException(h);
            }
            List<InetAddress> out = new java.util.ArrayList<>();
            for (String ip : ips) {
                out.add(InetAddress.getByName(ip));
            }
            return out;
        };
    }

    @Test
    @DisplayName("HTTPS para IP público é aceito")
    void publicHttpsAccepted() {
        var uri = SsrfUrlValidator.validate("https://203.0.113.10/hook",
                fixed("203.0.113.10", "203.0.113.10"));
        assertThat(uri.getScheme()).isEqualTo("https");
    }

    @Test
    @DisplayName("HTTP, credencial embutida e host ausente são recusados")
    void httpUserinfoAndMissingHostRejected() {
        assertThatThrownBy(() -> SsrfUrlValidator.validate("http://203.0.113.10/hook",
                SsrfUrlValidator.SYSTEM)).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> SsrfUrlValidator.validate("https://user:pass@203.0.113.10/hook",
                SsrfUrlValidator.SYSTEM)).hasMessageContaining("credencial");
        assertThatThrownBy(() -> SsrfUrlValidator.validate("https:///hook",
                SsrfUrlValidator.SYSTEM)).hasMessageContaining("host");
    }

    @Test
    @DisplayName("IP literal privado, loopback e link-local são recusados")
    void privateLiteralIpsRejected() {
        for (String ip : List.of("10.0.0.5", "192.168.1.1", "172.16.0.9", "127.0.0.1", "169.254.1.2")) {
            assertThatThrownBy(() -> SsrfUrlValidator.validate("https://" + ip + "/hook",
                    SsrfUrlValidator.SYSTEM))
                    .as("IP %s deveria ser recusado", ip)
                    .hasMessageContaining("privado ou reservado");
        }
    }

    @Test
    @DisplayName("nome que resolve para privado é recusado, mesmo com HTTPS")
    void nameResolvingToPrivateRejected() {
        assertThatThrownBy(() -> SsrfUrlValidator.validate("https://interno.exemplo.test/hook",
                fixed("interno.exemplo.test", "10.1.2.3")))
                .hasMessageContaining("privado ou reservado");
    }

    @Test
    @DisplayName("nome que resolve só para público é aceito; nome que não resolve é recusado")
    void publicNameAcceptedUnresolvableRejected() {
        var uri = SsrfUrlValidator.validate("https://publico.exemplo.test/hook",
                fixed("publico.exemplo.test", "203.0.113.20"));
        assertThat(uri.getHost()).isEqualTo("publico.exemplo.test");
        assertThatThrownBy(() -> SsrfUrlValidator.validate("https://ausente.exemplo.test/hook",
                fixed("outro.exemplo.test", "203.0.113.20")))
                .hasMessageContaining("não resolve");
    }

    @Test
    @DisplayName("isPublic barra loopback, site-local, link-local, multicast e any-local")
    void isPublicClassification() throws Exception {
        assertThat(SsrfUrlValidator.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SsrfUrlValidator.isPublic(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(SsrfUrlValidator.isPublic(InetAddress.getByName("10.0.0.1"))).isFalse();
        assertThat(SsrfUrlValidator.isPublic(InetAddress.getByName("224.0.0.1"))).isFalse();
    }
}
