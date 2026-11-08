package br.com.ziipsan.integration.hub;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * P3.37: limite de taxa por (tenant, integração) — balde de fichas com
 * reposição contínua: {@code capacity} fichas, repostas a
 * {@code refillPerMinute} por minuto. Estourar o limite NÃO é falha do
 * parceiro: o hub recusa antes de chamar, e a mensagem recusada não
 * entra na fila de falhas por esse motivo (é limite local, não erro de
 * integração) — quem chamou decide reagendar.
 *
 * <p>Determinístico (instantes explícitos) e de instância única por par
 * (tenant, integração) na fiação, como o {@link CircuitBreaker}.
 */
public final class IntegrationRateLimiter {

    private final int capacity;
    private final double refillPerMinute;
    private double tokens;
    private Instant lastRefillAt;

    public IntegrationRateLimiter(int capacity, double refillPerMinute) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacidade do limite de taxa deve ser >= 1: " + capacity);
        }
        if (refillPerMinute <= 0 || Double.isNaN(refillPerMinute)) {
            throw new IllegalArgumentException("reposição do limite de taxa deve ser > 0: " + refillPerMinute);
        }
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
        this.tokens = capacity;
        this.lastRefillAt = null;
    }

    /** Tenta consumir 1 ficha no instante dado. */
    public synchronized boolean tryAcquire(Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório no limite de taxa");
        refill(now);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Fichas disponíveis no instante (após a reposição) — para o painel de saúde. */
    public synchronized double availableTokens(Instant now) {
        Objects.requireNonNull(now, "instante é obrigatório no limite de taxa");
        refill(now);
        return tokens;
    }

    private void refill(Instant now) {
        if (lastRefillAt == null) {
            lastRefillAt = now;
            return;
        }
        if (now.isBefore(lastRefillAt)) {
            throw new IllegalArgumentException("instante retrocedeu no limite de taxa: " + now + " < " + lastRefillAt);
        }
        long elapsedMillis = Duration.between(lastRefillAt, now).toMillis();
        if (elapsedMillis > 0) {
            tokens = Math.min(capacity, tokens + elapsedMillis * refillPerMinute / 60_000.0);
            lastRefillAt = now;
        }
    }
}
