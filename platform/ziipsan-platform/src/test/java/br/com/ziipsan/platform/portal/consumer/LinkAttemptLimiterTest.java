package br.com.ziipsan.platform.portal.consumer;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.20 - bloqueio progressivo do vinculo. */
class LinkAttemptLimiterTest {
    private final LinkAttemptLimiter.Limits limits = new LinkAttemptLimiter.Limits(5, 5, 100, 4);

    @Test void abaixoDoLimitePermite() {
        assertThat(LinkAttemptLimiter.evaluate(4, 0, 0, limits).decision())
                .isEqualTo(LinkAttemptLimiter.Decision.ALLOW);
    }
    @Test void bloqueioCresceComOEstouro() {
        var first = LinkAttemptLimiter.evaluate(5, 0, 0, limits);
        var later = LinkAttemptLimiter.evaluate(8, 0, 0, limits);
        assertThat(first.decision()).isEqualTo(LinkAttemptLimiter.Decision.BLOCKED);
        assertThat(later.blockFor()).isGreaterThan(first.blockFor());
        assertThat(first.blockFor()).isEqualTo(Duration.ofMinutes(1));
    }
    @Test void alertaDeVolumeNoLimiar() {
        assertThat(LinkAttemptLimiter.evaluate(4, 0, 0, limits).volumeAlert()).isTrue();
    }
}
