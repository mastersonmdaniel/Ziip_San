package br.com.ziipsan.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2.26: assinatura HMAC-SHA-256 das entregas, com carimbo de tempo
 * contra repetição e sobreposição do segredo anterior na rotação.
 */
class WebhookSignerTest {

    private static final String BODY = "{\"tenantId\":\"t\",\"billId\":\"b\"}";
    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    @DisplayName("assinatura gerada verifica com o mesmo segredo, evento e corpo")
    void signAndVerifyRoundTrip() {
        String signature = WebhookSigner.sign("segredo", 1_700_000_000L, EVENT_ID, BODY);
        assertThat(signature).startsWith("v1=");
        assertThat(WebhookSigner.verify(signature, "segredo", null,
                1_700_000_000L, 1_700_000_100L, EVENT_ID, BODY)).isTrue();
    }

    @Test
    @DisplayName("segredo errado, corpo adulterado e evento trocado não verificam")
    void tamperingFailsVerification() {
        String signature = WebhookSigner.sign("segredo", 1_700_000_000L, EVENT_ID, BODY);
        assertThat(WebhookSigner.verify(signature, "outro", null,
                1_700_000_000L, 1_700_000_100L, EVENT_ID, BODY)).isFalse();
        assertThat(WebhookSigner.verify(signature, "segredo", null,
                1_700_000_000L, 1_700_000_100L, EVENT_ID, BODY + " ")).isFalse();
        assertThat(WebhookSigner.verify(signature, "segredo", null,
                1_700_000_000L, 1_700_000_100L, "22222222-2222-2222-2222-222222222222", BODY)).isFalse();
    }

    @Test
    @DisplayName("na rotação, a assinatura do segredo anterior verifica até o fim da sobreposição")
    void previousSecretVerifiesDuringOverlap() {
        String signature = WebhookSigner.sign("antigo", 1_700_000_000L, EVENT_ID, BODY);
        assertThat(WebhookSigner.verify(signature, "novo", "antigo",
                1_700_000_000L, 1_700_000_100L, EVENT_ID, BODY)).isTrue();
    }

    @Test
    @DisplayName("carimbo fora da janela de 5 minutos é rejeitado (contra repetição)")
    void staleTimestampRejected() {
        String signature = WebhookSigner.sign("segredo", 1_700_000_000L, EVENT_ID, BODY);
        assertThat(WebhookSigner.verify(signature, "segredo", null,
                1_700_000_000L, 1_700_000_000L + 301, EVENT_ID, BODY)).isFalse();
        assertThat(WebhookSigner.verify(signature, "segredo", null,
                1_700_000_000L, 1_700_000_000L + 300, EVENT_ID, BODY)).isTrue();
    }
}
