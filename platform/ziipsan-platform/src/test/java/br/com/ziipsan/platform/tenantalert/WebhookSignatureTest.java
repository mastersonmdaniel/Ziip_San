package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    @Test
    void signAndVerifyRoundTrip() {
        long ts = 1_700_000_000L;
        String signature = WebhookSignature.sign("segredo", ts, "{\"a\":1}");
        assertThat(signature).hasSize(64).matches("[0-9a-f]+");
        assertThat(WebhookSignature.verify("segredo", ts, "{\"a\":1}", signature)).isTrue();
    }

    @Test
    void tamperedPayloadOrTimestampOrSecretFailsVerification() {
        long ts = 1_700_000_000L;
        String signature = WebhookSignature.sign("segredo", ts, "{\"a\":1}");
        assertThat(WebhookSignature.verify("segredo", ts, "{\"a\":2}", signature)).isFalse();
        assertThat(WebhookSignature.verify("segredo", ts + 1, "{\"a\":1}", signature)).isFalse();
        assertThat(WebhookSignature.verify("outro-segredo", ts, "{\"a\":1}", signature)).isFalse();
    }

    @Test
    void timestampFreshnessWindowRejectsReplayOutsideTolerance() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        Duration tolerance = Duration.ofMinutes(5);
        assertThat(WebhookSignature.isTimestampFresh(1_700_000_000L - 299, now, tolerance)).isTrue();
        assertThat(WebhookSignature.isTimestampFresh(1_700_000_000L - 301, now, tolerance)).isFalse();
        assertThat(WebhookSignature.isTimestampFresh(1_700_000_000L + 301, now, tolerance)).isFalse();
    }
}
