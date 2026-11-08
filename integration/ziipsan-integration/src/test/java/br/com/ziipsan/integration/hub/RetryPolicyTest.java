package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.37: política padrão de tempo limite e repetição. Recuos calculados
 * à mão: base 500 ms dobrando -> 500, 1000, 2000, 4000, 8000 e teto de
 * 8000 daí em diante (500 * 2^5 = 16000 > 8000).
 */
class RetryPolicyTest {

    @Test
    @DisplayName("padrão do hub: 10 s de limite, 3 tentativas, recuo 500 ms dobrando até 8 s")
    void hubDefault() {
        RetryPolicy p = RetryPolicy.DEFAULT;
        assertThat(p.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.maxAttempts()).isEqualTo(3);
        assertThat(p.backoffForRetry(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(p.backoffForRetry(2)).isEqualTo(Duration.ofMillis(1000));
        assertThat(p.backoffForRetry(3)).isEqualTo(Duration.ofMillis(2000));
        assertThat(p.backoffForRetry(4)).isEqualTo(Duration.ofMillis(4000));
        assertThat(p.backoffForRetry(5)).isEqualTo(Duration.ofMillis(8000));
        assertThat(p.backoffForRetry(6)).isEqualTo(Duration.ofMillis(8000));
    }

    @Test
    @DisplayName("hasAnotherAttempt respeita o teto de tentativas")
    void attemptsBoundary() {
        RetryPolicy p = RetryPolicy.DEFAULT;
        assertThat(p.hasAnotherAttempt(1)).isTrue();
        assertThat(p.hasAnotherAttempt(2)).isTrue();
        assertThat(p.hasAnotherAttempt(3)).isFalse();
    }

    @Test
    @DisplayName("política inválida é recusada na construção")
    void invalidPolicy() {
        assertThatThrownBy(() -> new RetryPolicy(Duration.ZERO, 3, Duration.ofMillis(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(1), 0, Duration.ofMillis(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(Duration.ofSeconds(1), 3, Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("teto");
    }
}
