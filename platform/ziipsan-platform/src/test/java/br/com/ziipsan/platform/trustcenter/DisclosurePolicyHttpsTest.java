package br.com.ziipsan.platform.trustcenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.38 — CORREÇÃO D3 (revisão cruzada M3, RFC 9116): {@code Policy}
 * (§2.5.4) e {@code Canonical} (§2.5.3) são URIs {@code https:}, mas a
 * {@link DisclosurePolicy} aceitava {@code mailto:} nesses campos e o
 * {@link SecurityTxt} emitia {@code Policy: mailto:...} — arquivo que
 * um validador RFC 9116 reprova. A política agora recusa qualquer URL
 * não-https em {@code policyUrl}/{@code canonicalUrl} já na construção
 * (falha fechada, como o restante do pacote). O {@code Contact}
 * (§2.5.2) continua aceitando {@code mailto:} — a correção não
 * restringe o que a RFC permite.
 *
 * <p>Fixture de teste com domínio {@code example.invalid} (RFC 2606),
 * como na suíte original: não é o canal nem as URLs de produção.
 */
class DisclosurePolicyHttpsTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final String HTTPS_POLICY =
            "https://example.invalid/trust-center/politica-divulgacao-responsavel";
    private static final String HTTPS_CANONICAL =
            "https://example.invalid/.well-known/security.txt";

    @Test
    @DisplayName("P3.38 D3: policyUrl mailto: é recusada na construção da política (RFC 9116 §2.5.4)")
    void mailtoPolicyUrlIsRefused() {
        assertThatThrownBy(() -> new DisclosurePolicy("mailto:security@example.invalid",
                "mailto:sec@example.invalid", null, Duration.ofDays(2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyUrl")
                .hasMessageContaining("https://");
        // Não-https em geral também é recusado, não só o mailto: da sonda.
        assertThatThrownBy(() -> new DisclosurePolicy("mailto:security@example.invalid",
                "http://example.invalid/politica", null, Duration.ofDays(2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyUrl");
    }

    @Test
    @DisplayName("P3.38 D3: canonicalUrl mailto: é recusada na construção da política (RFC 9116 §2.5.3)")
    void mailtoCanonicalUrlIsRefused() {
        assertThatThrownBy(() -> new DisclosurePolicy("mailto:security@example.invalid",
                HTTPS_POLICY, "mailto:sec@example.invalid", Duration.ofDays(2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonicalUrl")
                .hasMessageContaining("https://");
        assertThatThrownBy(() -> new DisclosurePolicy("mailto:security@example.invalid",
                HTTPS_POLICY, "http://example.invalid/.well-known/security.txt",
                Duration.ofDays(2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonicalUrl");
    }

    @Test
    @DisplayName("P3.38 D3: policyUrl/canonicalUrl https: são aceitas e o render emite Policy:/Canonical: https:")
    void httpsPolicyAndCanonicalAreAcceptedAndRendered() {
        DisclosurePolicy policy = new DisclosurePolicy("mailto:security@example.invalid",
                HTTPS_POLICY, HTTPS_CANONICAL, Duration.ofDays(2), Duration.ofDays(90));
        String txt = SecurityTxt.render(policy, NOW.plus(Duration.ofDays(180)), NOW);
        assertThat(txt).contains("Policy: https://example.invalid/trust-center/politica-divulgacao-responsavel");
        assertThat(txt).contains("Canonical: https://example.invalid/.well-known/security.txt");
        assertThat(txt).doesNotContain("Policy: mailto:");
        assertThat(txt).doesNotContain("Canonical: mailto:");
    }

    @Test
    @DisplayName("P3.38 D3: Contact continua aceitando mailto: e https: (a correção não restringe o §2.5.2)")
    void mailtoContactIsStillAccepted() {
        assertThatCode(() -> new DisclosurePolicy("mailto:security@example.invalid",
                HTTPS_POLICY, null, Duration.ofDays(2), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> new DisclosurePolicy("https://example.invalid/relatar",
                HTTPS_POLICY, null, Duration.ofDays(2), null))
                .doesNotThrowAnyException();
    }
}
