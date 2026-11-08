package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.37: circuito aberto. Limiar 3, duração 60 s, t0 = 12:00:00Z:
 * abre na 3ª falha; em t0+59 s segue aberto; em t0+60 s é meio-aberto;
 * prova com sucesso fecha; prova com falha reabre por mais 60 s.
 */
class CircuitBreakerTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");

    private static CircuitBreaker breaker() {
        return new CircuitBreaker(3, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("abre exatamente no limiar de falhas consecutivas")
    void opensAtThreshold() {
        CircuitBreaker cb = breaker();
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        assertThat(cb.stateAt(T0)).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest(T0)).isTrue();
        cb.recordFailure(T0);
        assertThat(cb.consecutiveFailures()).isEqualTo(3);
        assertThat(cb.stateAt(T0)).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest(T0)).isFalse();
    }

    @Test
    @DisplayName("fronteira da duração: 59 s aberto, 60 s meio-aberto")
    void openDurationBoundary() {
        CircuitBreaker cb = breaker();
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        assertThat(cb.stateAt(T0.plusSeconds(59))).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest(T0.plusSeconds(59))).isFalse();
        assertThat(cb.stateAt(T0.plusSeconds(60))).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest(T0.plusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("sucesso na prova fecha e zera; sucesso comum também zera as consecutivas")
    void successClosesAndResets() {
        CircuitBreaker cb = breaker();
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        cb.recordSuccess();
        assertThat(cb.consecutiveFailures()).isZero();
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        assertThat(cb.stateAt(T0.plusSeconds(60))).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        cb.recordSuccess();
        assertThat(cb.stateAt(T0.plusSeconds(60))).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.openedAt()).isNull();
    }

    @Test
    @DisplayName("falha na prova reabre por outra duração inteira")
    void halfOpenFailureReopens() {
        CircuitBreaker cb = breaker();
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        cb.recordFailure(T0);
        Instant probe = T0.plusSeconds(60);
        assertThat(cb.stateAt(probe)).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        cb.recordFailure(probe);
        assertThat(cb.stateAt(probe.plusSeconds(59))).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.stateAt(probe.plusSeconds(60))).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(cb.openedAt()).isEqualTo(probe);
    }
}
