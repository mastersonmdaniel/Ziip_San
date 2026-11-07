package br.com.ziipsan.platform.trustcenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.38: o security.txt (RFC 9116) — campos obrigatórios, expiração e a
 * recusa de publicar política sem canal/prazo decididos.
 *
 * <p>ATENÇÃO: o canal e os prazos desta classe são FIXTURE DE TESTE
 * (domínio {@code example} da RFC 2606 e prazos arbitrários de relógio
 * fixo). Eles provam o formato e as recusas; NÃO são o canal oficial nem
 * o prazo de produção, que são decisão pendente do dono.
 */
class SecurityTxtTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static DisclosurePolicy publishablePolicy() {
        return new DisclosurePolicy(
                "mailto:security@example.invalid",
                "https://example.invalid/trust-center/politica-divulgacao-responsavel",
                "https://example.invalid/.well-known/security.txt",
                Duration.ofDays(2), Duration.ofDays(90));
    }

    @Test
    @DisplayName("P3.38: security.txt renderiza Contact, Expires, Policy, Canonical e Preferred-Languages (RFC 9116)")
    void rendersRfc9116Fields() {
        String txt = SecurityTxt.render(publishablePolicy(), NOW.plus(Duration.ofDays(180)), NOW);
        assertThat(txt).contains("Contact: mailto:security@example.invalid");
        assertThat(txt).contains("Expires: " + NOW.plus(Duration.ofDays(180)));
        assertThat(txt).contains("Policy: https://example.invalid/trust-center/politica-divulgacao-responsavel");
        assertThat(txt).contains("Canonical: https://example.invalid/.well-known/security.txt");
        assertThat(txt).contains("Preferred-Languages: pt-BR");
    }

    @Test
    @DisplayName("P3.38: política pendente (sem canal/prazo do dono) recusa gerar o security.txt")
    void pendingPolicyIsRefused() {
        assertThatThrownBy(() -> SecurityTxt.render(DisclosurePolicy.pending(), NOW.plus(Duration.ofDays(30)), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decisão pendente do dono");
    }

    @Test
    @DisplayName("P3.38: marcador de modelo no canal é recusado na construção da política")
    void placeholderContactIsRefused() {
        assertThatThrownBy(() -> new DisclosurePolicy("mailto:<CANAL-A-DEFINIR>", null, null,
                Duration.ofDays(2), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P3.38: Expires no passado ou a mais de 1 ano é recusado (RFC 9116 §2.5.5)")
    void invalidExpiryIsRefused() {
        assertThatThrownBy(() -> SecurityTxt.render(publishablePolicy(), NOW.minusSeconds(1), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expires");
        assertThatThrownBy(() -> SecurityTxt.render(publishablePolicy(),
                NOW.plus(Duration.ofDays(366)), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 ano");
        // A fronteira de exatamente 1 ano é aceita.
        assertThat(SecurityTxt.render(publishablePolicy(), NOW.plus(SecurityTxt.MAX_VALIDITY), NOW))
                .contains("Expires:");
    }
}
