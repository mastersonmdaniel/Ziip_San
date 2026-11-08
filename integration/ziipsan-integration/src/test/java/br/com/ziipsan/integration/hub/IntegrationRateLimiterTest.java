package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.37: limite de taxa. Capacidade 5, reposição 60/min = 1 ficha/s,
 * cálculos à mão: 5 aquisições passam e a 6ª falha em t0; em t0+3 s há
 * exatamente 3 fichas de volta; a reposição nunca passa da capacidade.
 */
class IntegrationRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    @DisplayName("capacidade esgota e a reposição é de 1 ficha por segundo")
    void bucketMath() {
        IntegrationRateLimiter limiter = new IntegrationRateLimiter(5, 60.0);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(T0)).as("aquisição %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire(T0)).isFalse();
        assertThat(limiter.availableTokens(T0.plusSeconds(3))).isEqualTo(3.0);
        assertThat(limiter.tryAcquire(T0.plusSeconds(3))).isTrue();
        assertThat(limiter.tryAcquire(T0.plusSeconds(3))).isTrue();
        assertThat(limiter.tryAcquire(T0.plusSeconds(3))).isTrue();
        assertThat(limiter.tryAcquire(T0.plusSeconds(3))).isFalse();
    }

    @Test
    @DisplayName("reposição é limitada à capacidade, mesmo após espera longa")
    void refillCappedAtCapacity() {
        IntegrationRateLimiter limiter = new IntegrationRateLimiter(5, 60.0);
        assertThat(limiter.tryAcquire(T0)).isTrue();
        assertThat(limiter.availableTokens(T0.plusSeconds(3600))).isEqualTo(5.0);
    }

    @Test
    @DisplayName("instante que retrocede é erro explícito, não reposição negativa")
    void clockBackwards() {
        IntegrationRateLimiter limiter = new IntegrationRateLimiter(5, 60.0);
        assertThat(limiter.tryAcquire(T0.plusSeconds(10))).isTrue();
        assertThatThrownBy(() -> limiter.tryAcquire(T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retrocedeu");
    }
}
