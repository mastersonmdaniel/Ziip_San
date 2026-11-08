package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correção D3 (pós-reprovação): o anti-replay aplicado no fluxo real de
 * verificação — {@link WebhookSignatureVerifier} é o verificador do lado
 * receptor e a autochecagem da {@link ReliableWebhookDelivery}. Assinatura
 * VÁLIDA com carimbo fora da tolerância é repetição e NÃO é aceita.
 */
class WebhookSignatureVerifierTest {

    private static final String SECRET = "segredo-de-ensaio";
    private static final String PAYLOAD = "{\"event_id\":\"x\"}";
    private static final long TS = 1_700_000_000L;
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    @Test
    void validSignatureWithFreshTimestampIsAccepted() {
        String signature = WebhookSignature.sign(SECRET, TS, PAYLOAD);
        assertThat(WebhookSignatureVerifier.accept(SECRET, TS, PAYLOAD, signature,
                Instant.ofEpochSecond(TS + 60), TOLERANCE)).isTrue();
    }

    @Test
    void validSignatureWithStaleTimestampIsRejectedAsReplay() {
        String signature = WebhookSignature.sign(SECRET, TS, PAYLOAD);
        // A assinatura continua criptograficamente válida — quem recusa é o anti-replay.
        assertThat(WebhookSignature.verify(SECRET, TS, PAYLOAD, signature)).isTrue();
        assertThat(WebhookSignatureVerifier.accept(SECRET, TS, PAYLOAD, signature,
                Instant.ofEpochSecond(TS + 301), TOLERANCE)).isFalse();
    }

    @Test
    void validSignatureWithFutureTimestampBeyondToleranceIsRejected() {
        String signature = WebhookSignature.sign(SECRET, TS, PAYLOAD);
        assertThat(WebhookSignatureVerifier.accept(SECRET, TS, PAYLOAD, signature,
                Instant.ofEpochSecond(TS - 301), TOLERANCE)).isFalse();
    }

    @Test
    void tamperedPayloadIsRejectedEvenWhenFresh() {
        String signature = WebhookSignature.sign(SECRET, TS, PAYLOAD);
        assertThat(WebhookSignatureVerifier.accept(SECRET, TS, "{\"event_id\":\"y\"}", signature,
                Instant.ofEpochSecond(TS), TOLERANCE)).isFalse();
    }

    @Test
    void signedRequestOverloadAppliesTheSameGate() {
        String signature = WebhookSignature.sign(SECRET, TS, PAYLOAD);
        var request = new TenantAlertPorts.SignedWebhookRequest(TenantAlertFixtures.TENANT, UUID.randomUUID(),
                "https://cliente.exemplo.invalid/webhooks/ziipsan", PAYLOAD, TS, signature);
        assertThat(WebhookSignatureVerifier.accept(request, SECRET,
                Instant.ofEpochSecond(TS + 10), TOLERANCE)).isTrue();
        assertThat(WebhookSignatureVerifier.accept(request, SECRET,
                Instant.ofEpochSecond(TS + 3600), TOLERANCE)).isFalse();
    }
}
