package br.com.ziipsan.platform.security;

import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RateLimitStore} em memória, para testes unitários e como fallback de
 * construção. NÃO compartilha entre réplicas nem processos — em produção o store
 * é o {@link JdbcRateLimitStore}. Uma entrada por chave; a janela antiga é
 * descartada quando chega uma janela mais nova.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private record Bucket(long windowStart, int count) {
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public int increment(String key, long windowStartEpochSecond) {
        Bucket updated = buckets.compute(key, (k, current) -> {
            if (current == null || current.windowStart() < windowStartEpochSecond) {
                return new Bucket(windowStartEpochSecond, 1);
            }
            return new Bucket(current.windowStart(), current.count() + 1);
        });
        return updated.count();
    }
}
