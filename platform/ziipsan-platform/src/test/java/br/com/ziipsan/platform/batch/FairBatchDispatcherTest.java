package br.com.ziipsan.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.1 / RNF-25: fila justa. Um tenant grande não ocupa mais de 50%
 * dos slots, e o tenant pequeno que chega depois começa sem esperar o
 * lote grande terminar.
 */
class FairBatchDispatcherTest {

    @Test
    @DisplayName("tenant grande ocupa no máximo 50% dos slots; o pequeno completa sem esperar o grande")
    void largeTenantCappedAndSmallTenantNotDelayed() throws Exception {
        try (FairBatchDispatcher dispatcher = new FairBatchDispatcher(4)) {
            assertThat(dispatcher.perTenantCap()).isEqualTo(2);
            UUID large = UUID.randomUUID();
            UUID small = UUID.randomUUID();
            CountDownLatch releaseLarge = new CountDownLatch(1);
            AtomicInteger largeActive = new AtomicInteger();
            AtomicInteger largeMaxActive = new AtomicInteger();
            List<Future<String>> largeFutures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                largeFutures.add(dispatcher.submit(large, () -> {
                    int current = largeActive.incrementAndGet();
                    largeMaxActive.accumulateAndGet(current, Math::max);
                    try {
                        assertThat(releaseLarge.await(30, TimeUnit.SECONDS)).isTrue();
                    } finally {
                        largeActive.decrementAndGet();
                    }
                    return "large";
                }));
            }
            // Espera o teto do grande ser atingido (2 de 4 slots).
            long deadline = System.currentTimeMillis() + 10_000;
            while (largeActive.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(largeActive.get()).isEqualTo(2);

            // O pequeno chega depois, com o lote grande ainda bloqueado.
            Future<String> smallFuture = dispatcher.submit(small, () -> "small");
            assertThat(smallFuture.get(10, TimeUnit.SECONDS)).isEqualTo("small");
            assertThat(largeMaxActive.get()).isEqualTo(2);

            releaseLarge.countDown();
            for (Future<String> future : largeFutures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("large");
            }
            assertThat(largeMaxActive.get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("tarefas do mesmo tenant saem em ordem FIFO")
    void sameTenantTasksRunInSubmissionOrder() throws Exception {
        try (FairBatchDispatcher dispatcher = new FairBatchDispatcher(2)) {
            UUID tenant = UUID.randomUUID();
            List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
            // Cap por tenant com 2 slots é 1: o tenant corre em série, logo a ordem é exata.
            List<Future<String>> futures = new ArrayList<>();
            for (String name : List.of("primeira", "segunda", "terceira")) {
                futures.add(dispatcher.submit(tenant, () -> {
                    order.add(name);
                    return name;
                }));
            }
            for (Future<String> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertThat(order).containsExactly("primeira", "segunda", "terceira");
        }
    }

    @Test
    @DisplayName("slots = 1: o teto por tenant é 1 (piso do RNF-25)")
    void singleSlotCapIsOne() {
        try (FairBatchDispatcher dispatcher = new FairBatchDispatcher(1)) {
            assertThat(dispatcher.perTenantCap()).isEqualTo(1);
        }
    }
}
