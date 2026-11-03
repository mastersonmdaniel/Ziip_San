package br.com.ziipsan.platform.web;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório em memória de respostas de POST idempotente (P1.11).
 *
 * <p>Chave de escopo: tenant + método + caminho + {@code Idempotency-Key} do cliente.
 * A mesma chave, no mesmo tenant, devolve a mesma resposta gravada (status e corpo),
 * sem reexecutar a operação. Chave de outro tenant nunca reaproveita a resposta:
 * o replay entre tenants vazaria a existência (e o conteúdo) de uma operação alheia.
 *
 * <p>Limites honestos desta v0: o store vive só no processo da API. Entre réplicas,
 * a mesma chave pode executar duas vezes (cada réplica tem o seu mapa); a troca por
 * um store persistente (tabela por tenant) é trabalho futuro e não muda o contrato
 * do controller. Entradas expiram em 24 h e o mapa é limitado (varredura preguiçosa
 * ao gravar); chave sem valor ou em branco nunca chega aqui — o controller só
 * consulta o store quando o cabeçalho está presente e não vazio.
 */
@Component
public class IdempotencyStore {

    /** Resposta gravada de um POST: status HTTP e corpo já serializado pelo controller. */
    public record StoredResponse(int status, Object body) {
    }

    /** Chave de escopo de uma operação idempotente. */
    public record Key(UUID tenantId, String method, String path, String idempotencyKey) {
    }

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final Clock clock;
    private final Duration ttl;
    private final int maxEntries;
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Key, Object> locks = new ConcurrentHashMap<>();

    private record Entry(StoredResponse response, Instant expiresAt) {
    }

    public IdempotencyStore() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_ENTRIES);
    }

    IdempotencyStore(Clock clock, Duration ttl, int maxEntries) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
    }

    /** Trava por chave: dois POSTs simultâneos com a mesma chave executam em série. */
    public Object lockFor(Key key) {
        return locks.computeIfAbsent(key, k -> new Object());
    }

    public Optional<StoredResponse> find(Key key) {
        Entry entry = entries.get(key);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            if (entry != null) {
                entries.remove(key, entry);
            }
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void store(Key key, StoredResponse response) {
        if (entries.size() >= maxEntries) {
            Instant now = clock.instant();
            entries.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
        }
        entries.put(key, new Entry(response, clock.instant().plus(ttl)));
    }
}
