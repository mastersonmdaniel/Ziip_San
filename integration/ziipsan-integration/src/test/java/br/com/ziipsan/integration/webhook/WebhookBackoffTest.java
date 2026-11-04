package br.com.ziipsan.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.26: recuo exponencial das tentativas, base 60 s, teto 1 h. */
class WebhookBackoffTest {

    @Test
    @DisplayName("o atraso dobra a cada tentativa até o teto de 1 hora")
    void delayDoublesUpToCap() {
        assertThat(WebhookBackoff.delayWithoutJitter(1)).isEqualTo(Duration.ofSeconds(60));
        assertThat(WebhookBackoff.delayWithoutJitter(2)).isEqualTo(Duration.ofSeconds(120));
        assertThat(WebhookBackoff.delayWithoutJitter(3)).isEqualTo(Duration.ofSeconds(240));
        assertThat(WebhookBackoff.delayWithoutJitter(4)).isEqualTo(Duration.ofSeconds(480));
        assertThat(WebhookBackoff.delayWithoutJitter(5)).isEqualTo(Duration.ofSeconds(960));
        assertThat(WebhookBackoff.delayWithoutJitter(6)).isEqualTo(Duration.ofSeconds(1920));
        assertThat(WebhookBackoff.delayWithoutJitter(7)).isEqualTo(Duration.ofSeconds(3600));
        assertThat(WebhookBackoff.delayWithoutJitter(99)).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    @DisplayName("o atraso com jitter nunca fica abaixo da base nem acima de base + 30%")
    void jitterStaysWithinThirtyPercent() {
        for (int i = 0; i < 200; i++) {
            Duration delay = WebhookBackoff.nextDelay(3);
            assertThat(delay).isBetween(Duration.ofSeconds(240), Duration.ofSeconds(312));
        }
    }
}
