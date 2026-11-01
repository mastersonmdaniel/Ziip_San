package br.com.ziipsan.core.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextHolderTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("setTenant e getTenant fazem round-trip")
    void setAndGetTenant() {
        TenantContextHolder.setTenant("empresa_a");

        assertThat(TenantContextHolder.getTenant()).isEqualTo("empresa_a");
        assertThat(TenantContextHolder.hasTenant()).isTrue();
    }

    @Test
    @DisplayName("setTenantId e getTenantId fazem round-trip")
    void setAndGetTenantId() {
        UUID tenantId = UUID.randomUUID();

        TenantContextHolder.setTenantId(tenantId);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("hasTenant é falso antes de qualquer define")
    void hasTenantIsFalseByDefault() {
        assertThat(TenantContextHolder.hasTenant()).isFalse();
        assertThat(TenantContextHolder.getTenant()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("clear remove tenant e tenantId")
    void clearRemovesEverything() {
        TenantContextHolder.setTenant("empresa_a");
        TenantContextHolder.setTenantId(UUID.randomUUID());

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.hasTenant()).isFalse();
        assertThat(TenantContextHolder.getTenant()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("contexto é isolado por thread: vazar tenant seria falha de isolamento de dados")
    void contextIsThreadLocal() throws Exception {
        TenantContextHolder.setTenant("empresa_principal");
        TenantContextHolder.setTenantId(UUID.randomUUID());

        AtomicReference<String> seenTenant = new AtomicReference<>();
        AtomicReference<UUID> seenTenantId = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            seenTenant.set(TenantContextHolder.getTenant());
            seenTenantId.set(TenantContextHolder.getTenantId());
            done.countDown();
        });
        worker.start();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(seenTenant.get())
                .as("uma thread nova não pode herdar o tenant de outra thread")
                .isNull();
        assertThat(seenTenantId.get()).isNull();

        assertThat(TenantContextHolder.getTenant())
                .as("o tenant da thread principal não pode ter sido alterado")
                .isEqualTo("empresa_principal");

        worker.join();
    }

    @Test
    @DisplayName("threads concorrentes não compartilham contexto entre si")
    void concurrentThreadsDoNotShareContext() throws Exception {
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger mismatches = new java.util.concurrent.atomic.AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                final String expected = "empresa_" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        TenantContextHolder.setTenant(expected);
                        Thread.sleep(20);
                        if (!expected.equals(TenantContextHolder.getTenant())) {
                            mismatches.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        TenantContextHolder.clear();
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(mismatches.get())
                .as("nenhuma thread pode ler o tenant de outra")
                .isZero();
        assertThat(TenantContextHolder.getTenant())
                .as("o contexto da thread principal permanece intocado")
                .isNull();
    }
}
