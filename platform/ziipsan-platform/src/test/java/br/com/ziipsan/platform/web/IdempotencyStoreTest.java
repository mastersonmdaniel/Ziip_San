package br.com.ziipsan.platform.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.11: replay, isolamento por tenant e expiração do store em memória (sem banco). */
class IdempotencyStoreTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-03T10:00:00Z"));

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        void advance(Duration d) {
            now.updateAndGet(i -> i.plus(d));
        }
    }

    @Test
    @DisplayName("mesma chave no mesmo tenant devolve a resposta gravada")
    void replaySameKeySameTenant() {
        var store = new IdempotencyStore(Clock.systemUTC(), Duration.ofHours(24), 100);
        var key = new IdempotencyStore.Key(TENANT_A, "POST", "/api/v1/users", "k-1");
        assertThat(store.find(key)).isEmpty();
        store.store(key, new IdempotencyStore.StoredResponse(201, "corpo"));
        assertThat(store.find(key)).contains(new IdempotencyStore.StoredResponse(201, "corpo"));
    }

    @Test
    @DisplayName("mesma chave em outro tenant não reaproveita a resposta")
    void otherTenantDoesNotReplay() {
        var store = new IdempotencyStore(Clock.systemUTC(), Duration.ofHours(24), 100);
        store.store(new IdempotencyStore.Key(TENANT_A, "POST", "/api/v1/users", "k-1"),
                new IdempotencyStore.StoredResponse(201, "corpo"));
        assertThat(store.find(new IdempotencyStore.Key(TENANT_B, "POST", "/api/v1/users", "k-1"))).isEmpty();
    }

    @Test
    @DisplayName("entrada expirada não é devolvida")
    void expiredEntryIsNotReturned() {
        var clock = new MutableClock();
        var store = new IdempotencyStore(clock, Duration.ofHours(24), 100);
        var key = new IdempotencyStore.Key(TENANT_A, "POST", "/api/v1/users", "k-1");
        store.store(key, new IdempotencyStore.StoredResponse(201, "corpo"));
        clock.advance(Duration.ofHours(25));
        assertThat(store.find(key)).isEmpty();
    }
}
