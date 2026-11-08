package br.com.ziipsan.integration.hub;

import java.time.Duration;

/**
 * P3.37: política PADRÃO de tempo limite e de repetição do hub. Vale
 * para todo conector que não declarar política própria na fiação; os
 * valores do padrão são constantes nomeadas desta classe (não vêm de
 * parâmetro do tenant — o aceite não os lista) e cada conector pode
 * receber uma instância mais estrita na construção do despacho.
 *
 * <p>Recuo exponencial determinístico: tentativa {@code n} (1 = primeira
 * repetição) espera {@code min(base * 2^(n-1), teto)}. Sem jitter: o
 * jitter, quando a fiação o quiser, entra no {@code Sleeper} dela; a
 * política precisa ser recomputável em teste.
 */
public record RetryPolicy(Duration timeout, int maxAttempts, Duration baseBackoff, Duration maxBackoff) {

    /** Padrão do hub: limite de 10 s por tentativa, 3 tentativas, recuo de 500 ms dobrando até 8 s. */
    public static final RetryPolicy DEFAULT = new RetryPolicy(
            Duration.ofSeconds(10), 3, Duration.ofMillis(500), Duration.ofSeconds(8));

    public RetryPolicy {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("tempo limite da política deve ser positivo: " + timeout);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("a política exige ao menos 1 tentativa: " + maxAttempts);
        }
        if (baseBackoff == null || baseBackoff.isNegative()) {
            throw new IllegalArgumentException("recuo base da política não pode ser negativo: " + baseBackoff);
        }
        if (maxBackoff == null || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("teto do recuo não pode ser menor que o recuo base: " + maxBackoff);
        }
    }

    /** Ainda cabe outra tentativa depois de {@code attemptsMade} tentativas feitas? */
    public boolean hasAnotherAttempt(int attemptsMade) {
        if (attemptsMade < 1) {
            throw new IllegalArgumentException("tentativas feitas deve ser >= 1: " + attemptsMade);
        }
        return attemptsMade < maxAttempts;
    }

    /**
     * Recuo antes da repetição de número {@code retryNumber} (1 = primeira
     * repetição, logo após a 1ª tentativa): base * 2^(n-1), limitado ao teto.
     */
    public Duration backoffForRetry(int retryNumber) {
        if (retryNumber < 1) {
            throw new IllegalArgumentException("número da repetição deve ser >= 1: " + retryNumber);
        }
        long factor = 1L << Math.min(retryNumber - 1, 20);
        Duration backoff = baseBackoff.multipliedBy(factor);
        return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
    }
}
