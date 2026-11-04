package br.com.ziipsan.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ziipsan.core.parameters.TenantTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.1: disparo duplo, retomada sem reprocessar DONE, falha no meio
 * do lote e data de referência no fuso do tenant — sobre o store em
 * memória e um executor falso (sem banco, sem Batch).
 */
class BatchJobServiceTest {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2027-02-01T02:00:00Z"), ZoneOffset.UTC);

    private FairBatchDispatcher dispatcher;

    @AfterEach
    void closeDispatcher() {
        if (dispatcher != null) {
            dispatcher.close();
        }
    }

    private BatchJobService service(BatchJobStore store, BatchRouteExecutor executor,
                                    Map<String, RouteHandler> handlers) {
        dispatcher = new FairBatchDispatcher(4);
        return new BatchJobService(store, executor, dispatcher, handlers, FIXED);
    }

    private static BatchRouteExecutor directExecutor() {
        return (context, handler) -> handler.execute(context);
    }

    @Test
    @DisplayName("disparo duplo do mesmo (tenant, ciclo) não corre e é registrado como recusa")
    void duplicateDispatchIsRefusedAndRecorded() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        BatchJobService svc = service(store, directExecutor(), Map.of(
                "rota-a", ctx -> counts.computeIfAbsent("rota-a", k -> new AtomicInteger()).incrementAndGet()));
        UUID tenant = UUID.randomUUID();
        TenantTime time = TenantTime.of("America/Sao_Paulo", FIXED);

        BatchJobService.DispatchResult first =
                svc.dispatch(tenant, "2027-02", List.of("rota-a"), time, null, null);
        assertThat(first.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        BatchJobService.DispatchResult second =
                svc.dispatch(tenant, "2027-02", List.of("rota-a"), time, null, null);

        assertThat(second.outcome()).isEqualTo(BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE);
        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(counts.get("rota-a").get()).isEqualTo(1);
        assertThat(store.dispatches(tenant, "2027-02")).hasSize(2);
        assertThat(store.dispatches(tenant, "2027-02").get(1).outcome())
                .isEqualTo(BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE);
        assertThat(store.getRun(first.runId(), tenant).status())
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
    }

    @Test
    @DisplayName("disparo duplo concorrente: exatamente um aceito e um recusado")
    void concurrentDuplicateDispatch() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        BatchJobService svc = service(store, directExecutor(),
                Map.of("rota-a", ctx -> {
                }));
        UUID tenant = UUID.randomUUID();
        TenantTime time = TenantTime.of("America/Sao_Paulo", FIXED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<BatchJobService.DispatchResult> results =
                java.util.Collections.synchronizedList(new ArrayList<>());
        Runnable attempt = () -> {
            ready.countDown();
            try {
                go.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            results.add(svc.dispatch(tenant, "2027-02", List.of("rota-a"), time, null, null));
        };
        Thread t1 = new Thread(attempt);
        Thread t2 = new Thread(attempt);
        t1.start();
        t2.start();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        t1.join(10_000);
        t2.join(10_000);
        assertThat(results).hasSize(2);
        assertThat(results.stream().map(BatchJobService.DispatchResult::outcome).toList())
                .containsExactlyInAnyOrder(BatchJobStore.DispatchOutcome.ACCEPTED,
                        BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE);
        for (BatchJobService.DispatchResult result : results) {
            if (result.outcome() == BatchJobStore.DispatchOutcome.ACCEPTED) {
                assertThat(result.completion().get(10, TimeUnit.SECONDS))
                        .isEqualTo(BatchJobStore.JobRunStatus.DONE);
            }
        }
    }

    @Test
    @DisplayName("falha no meio do lote: retomada executa só as pendentes, sem reprocessar DONE")
    void resumeAfterMidBatchFailureSkipsDoneRoutes() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        Set<String> failOnce = ConcurrentHashMap.newKeySet();
        failOnce.add("rota-2");
        Map<String, RouteHandler> handlers = Map.of(
                "rota-1", ctx -> counts.computeIfAbsent("rota-1", k -> new AtomicInteger()).incrementAndGet(),
                "rota-2", ctx -> {
                    counts.computeIfAbsent("rota-2", k -> new AtomicInteger()).incrementAndGet();
                    if (failOnce.remove("rota-2")) {
                        throw new RuntimeException("falha simulada no meio do lote");
                    }
                },
                "rota-3", ctx -> counts.computeIfAbsent("rota-3", k -> new AtomicInteger()).incrementAndGet());
        BatchJobService svc = service(store, directExecutor(), handlers);
        UUID tenant = UUID.randomUUID();
        TenantTime time = TenantTime.of("America/Sao_Paulo", FIXED);

        BatchJobService.DispatchResult first =
                svc.dispatch(tenant, "2027-02", List.of("rota-1", "rota-2", "rota-3"), time, null, null);
        assertThat(first.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.FAILED);
        assertThat(store.routes(first.runId(), tenant).stream()
                .map(r -> r.route() + "=" + r.status()).toList())
                .containsExactly("rota-1=DONE", "rota-2=FAILED", "rota-3=PENDING");
        assertThat(counts.get("rota-3")).isNull();

        BatchJobService.ResumeResult resumed = svc.resume(first.runId(), tenant);
        assertThat(resumed.resumed()).isTrue();
        assertThat(resumed.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        assertThat(counts.get("rota-1").get()).isEqualTo(1);
        assertThat(counts.get("rota-2").get()).isEqualTo(2);
        assertThat(counts.get("rota-3").get()).isEqualTo(1);
        assertThat(store.routes(first.runId(), tenant).stream()
                .map(BatchJobStore.JobRoute::status).toList())
                .containsExactly(BatchJobStore.RouteStatus.DONE, BatchJobStore.RouteStatus.DONE,
                        BatchJobStore.RouteStatus.DONE);
    }

    @Test
    @DisplayName("retomada de execução DONE não reexecuta nada")
    void resumeOfDoneRunDoesNothing() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        AtomicInteger count = new AtomicInteger();
        BatchJobService svc = service(store, directExecutor(),
                Map.of("rota-a", ctx -> count.incrementAndGet()));
        UUID tenant = UUID.randomUUID();
        BatchJobService.DispatchResult first = svc.dispatch(tenant, "2027-02", List.of("rota-a"),
                TenantTime.of("America/Sao_Paulo", FIXED), null, null);
        assertThat(first.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        BatchJobService.ResumeResult resumed = svc.resume(first.runId(), tenant);
        assertThat(resumed.resumed()).isFalse();
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("execução interrompida: rota RUNNING órfã volta a PENDING na retomada")
    void interruptedRunResetsOrphanRunningRoutes() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        AtomicInteger count = new AtomicInteger();
        BatchJobService svc = service(store, directExecutor(),
                Map.of("rota-a", ctx -> count.incrementAndGet()));
        UUID tenant = UUID.randomUUID();
        BatchJobService.DispatchResult first = svc.dispatch(tenant, "2027-02", List.of("rota-a"),
                TenantTime.of("America/Sao_Paulo", FIXED), null, null);
        assertThat(first.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        // Simula o processo morto no meio da rota: execução INTERRUPTED
        // com a rota órfã em RUNNING (estado que só um crash produz).
        BatchJobStore.JobRoute route = store.routes(first.runId(), tenant).get(0);
        store.markRunInterrupted(first.runId(), tenant, FIXED.instant());
        store.markRouteRunning(route.id(), tenant, FIXED.instant());
        BatchJobService.ResumeResult resumed = svc.resume(first.runId(), tenant);
        assertThat(resumed.resumed()).isTrue();
        assertThat(resumed.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("data de referência e fuso gravados são os do tenant no instante do disparo")
    void referenceDateUsesTenantTimezone() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        BatchJobService svc = service(store, directExecutor(), Map.of("rota-a", ctx -> {
        }));
        // 2027-02-01T02:00Z: em Manaus (UTC-4) ainda é 31/01; em Noronha (UTC-2) já é 01/02.
        UUID manaus = UUID.randomUUID();
        BatchJobService.DispatchResult r1 = svc.dispatch(manaus, "2027-01",
                List.of("rota-a"), TenantTime.of("America/Manaus", FIXED), null, null);
        assertThat(r1.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        assertThat(store.getRun(r1.runId(), manaus).referenceDate().toString()).isEqualTo("2027-01-31");
        assertThat(store.getRun(r1.runId(), manaus).timezone()).isEqualTo("America/Manaus");

        UUID noronha = UUID.randomUUID();
        BatchJobService.DispatchResult r2 = svc.dispatch(noronha, "2027-02",
                List.of("rota-a"), TenantTime.of("America/Noronha", FIXED), null, null);
        assertThat(r2.completion().get(10, TimeUnit.SECONDS))
                .isEqualTo(BatchJobStore.JobRunStatus.DONE);
        assertThat(store.getRun(r2.runId(), noronha).referenceDate().toString()).isEqualTo("2027-02-01");
        assertThat(store.getRun(r2.runId(), noronha).timezone()).isEqualTo("America/Noronha");
    }

    @Test
    @DisplayName("mesmo (tenant, ciclo) em tenants diferentes não colide")
    void sameCycleInDifferentTenantsIsIndependent() throws Exception {
        InMemoryBatchJobStore store = new InMemoryBatchJobStore();
        BatchJobService svc = service(store, directExecutor(), Map.of("rota-a", ctx -> {
        }));
        TenantTime time = TenantTime.of("America/Sao_Paulo", FIXED);
        BatchJobService.DispatchResult a =
                svc.dispatch(UUID.randomUUID(), "2027-02", List.of("rota-a"), time, null, null);
        BatchJobService.DispatchResult b =
                svc.dispatch(UUID.randomUUID(), "2027-02", List.of("rota-a"), time, null, null);
        assertThat(a.outcome()).isEqualTo(BatchJobStore.DispatchOutcome.ACCEPTED);
        assertThat(b.outcome()).isEqualTo(BatchJobStore.DispatchOutcome.ACCEPTED);
        assertThat(a.runId()).isNotEqualTo(b.runId());
    }
}
