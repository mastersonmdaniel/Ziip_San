package br.com.ziipsan.platform.batch;

import br.com.ziipsan.core.parameters.TenantTime;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * P2.1: disparo, execução e retomada de lotes.
 *
 * <p>Disparo: cria a execução QUEUED com as rotas PENDING, registra o
 * disparo como ACCEPTED e submete a execução à fila justa. Se (tenant,
 * ciclo) já tem execução, nada corre: o disparo é registrado como
 * REFUSED_DUPLICATE e o resultado carrega a execução existente.
 *
 * <p>Execução: as rotas correm em sequência, na ordem declarada, cada
 * uma como um Job Spring Batch. A primeira rota que falha aborta a
 * execução (status FAILED); as rotas restantes ficam PENDING. A
 * retomada ({@link #resume}) executa só as rotas não concluídas —
 * PENDING, FAILED (nova tentativa) e RUNNING órfã (devolvida a PENDING
 * antes) — e nunca reprocessa uma rota DONE.
 *
 * <p>Interrupção: se a tarefa da execução morre fora de uma rota (ex.:
 * o processo cai), a execução fica INTERRUPTED e as rotas RUNNING
 * ficam órfãs até a próxima retomada, que as devolve a PENDING. Um
 * processo morto no meio de uma rota nunca deixa a rota como DONE sem
 * tê-la concluído: DONE só é marcado depois do retorno do executor.
 *
 * <p>Tempo: a data de referência é a data local do tenant no instante
 * do disparo ({@link TenantTime}), e o fuso IANA fica gravado na
 * execução. O agendamento (quando) é do {@link BatchCalendar} e chega
 * aqui como {@code scheduledFor}; sem ele, o disparo é imediato.
 */
public final class BatchJobService {

    public record DispatchResult(BatchJobStore.DispatchOutcome outcome, UUID runId,
                                 Future<BatchJobStore.JobRunStatus> completion) {
    }

    public record ResumeResult(boolean resumed, UUID runId,
                               Future<BatchJobStore.JobRunStatus> completion) {
    }

    private final BatchJobStore store;
    private final BatchRouteExecutor routeExecutor;
    private final FairBatchDispatcher dispatcher;
    private final Map<String, RouteHandler> handlers;
    private final Clock clock;

    public BatchJobService(BatchJobStore store, BatchRouteExecutor routeExecutor,
                           FairBatchDispatcher dispatcher, Map<String, RouteHandler> handlers,
                           Clock clock) {
        this.store = Objects.requireNonNull(store, "O store é obrigatório");
        this.routeExecutor = Objects.requireNonNull(routeExecutor, "O executor é obrigatório");
        this.dispatcher = Objects.requireNonNull(dispatcher, "O dispatcher é obrigatório");
        this.handlers = new ConcurrentHashMap<>(Objects.requireNonNull(handlers, "Os handlers são obrigatórios"));
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório");
    }

    /** Registra (ou substitui) o handler de uma rota. */
    public void registerHandler(String route, RouteHandler handler) {
        Objects.requireNonNull(route, "A rota é obrigatória");
        Objects.requireNonNull(handler, "O handler é obrigatório");
        handlers.put(route, handler);
    }

    /**
     * Dispara um lote. A data de referência é a data local do tenant no
     * instante do disparo; o fuso gravado é o de {@code tenantTime}.
     */
    public DispatchResult dispatch(UUID tenantId, String cycleRef, List<String> routes,
                                   TenantTime tenantTime, UUID triggeredBy, Instant scheduledFor) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(cycleRef, "O ciclo é obrigatório");
        Objects.requireNonNull(tenantTime, "O tempo do tenant é obrigatório");
        BatchJobStore.JobRun run;
        try {
            run = store.createRun(tenantId, cycleRef, routes, tenantTime.nowLocalDate(),
                    tenantTime.zone().getId(), triggeredBy, scheduledFor);
        } catch (BatchJobStore.DuplicateJobRunException duplicate) {
            BatchJobStore.JobRun existing = store.findRun(tenantId, cycleRef).orElseThrow();
            store.recordDispatch(tenantId, cycleRef, BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE,
                    existing.id(), triggeredBy,
                    "Já existe uma execução para este tenant e ciclo: " + existing.id());
            return new DispatchResult(BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE,
                    existing.id(), CompletableFuture.completedFuture(existing.status()));
        }
        store.recordDispatch(tenantId, cycleRef, BatchJobStore.DispatchOutcome.ACCEPTED,
                run.id(), triggeredBy, null);
        Future<BatchJobStore.JobRunStatus> completion =
                dispatcher.submit(tenantId, () -> runExecution(run));
        return new DispatchResult(BatchJobStore.DispatchOutcome.ACCEPTED, run.id(), completion);
    }

    /**
     * Retoma uma execução interrompida ou falha. Execução DONE não
     * retoma (nada a fazer); qualquer outra volta à fila executando só
     * as rotas não concluídas.
     */
    public ResumeResult resume(UUID runId, UUID tenantId) {
        Objects.requireNonNull(runId, "A execução é obrigatória");
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        BatchJobStore.JobRun run = store.getRun(runId, tenantId);
        if (run.status() == BatchJobStore.JobRunStatus.DONE) {
            return new ResumeResult(false, runId,
                    CompletableFuture.completedFuture(BatchJobStore.JobRunStatus.DONE));
        }
        store.resetInterruptedRoutes(runId, tenantId);
        Future<BatchJobStore.JobRunStatus> completion =
                dispatcher.submit(tenantId, () -> runExecution(run));
        return new ResumeResult(true, runId, completion);
    }

    private BatchJobStore.JobRunStatus runExecution(BatchJobStore.JobRun run) {
        UUID runId = run.id();
        UUID tenantId = run.tenantId();
        store.markRunRunning(runId, tenantId, clock.instant());
        try {
            for (BatchJobStore.JobRoute route : store.routes(runId, tenantId)) {
                if (route.status() == BatchJobStore.RouteStatus.DONE) {
                    continue;
                }
                RouteHandler handler = handlers.get(route.route());
                if (handler == null) {
                    store.markRouteFailed(route.id(), tenantId,
                            "Sem handler registrado para a rota: " + route.route(),
                            clock.instant());
                    store.markRunFailed(runId, tenantId, clock.instant());
                    return BatchJobStore.JobRunStatus.FAILED;
                }
                store.markRouteRunning(route.id(), tenantId, clock.instant());
                try {
                    routeExecutor.execute(new RouteExecutionContext(
                            runId, tenantId, run.cycleRef(), route.route(),
                            route.attempts() + 1, run.referenceDate(),
                            java.time.ZoneId.of(run.timezone()), clock), handler);
                } catch (Exception e) {
                    String message = e.getMessage() == null
                            ? e.getClass().getSimpleName() : e.getMessage();
                    store.markRouteFailed(route.id(), tenantId, message, clock.instant());
                    store.markRunFailed(runId, tenantId, clock.instant());
                    return BatchJobStore.JobRunStatus.FAILED;
                }
                store.markRouteDone(route.id(), tenantId, clock.instant());
            }
        } catch (RuntimeException e) {
            // Falha fora de uma rota (ex.: o store): a execução fica
            // INTERRUPTED e as rotas RUNNING órfãs voltam na retomada.
            // Uma rota interrompida pelo executor cai no catch acima e
            // vira FAILED (nova tentativa na retomada), nunca DONE.
            store.markRunInterrupted(runId, tenantId, clock.instant());
            return BatchJobStore.JobRunStatus.INTERRUPTED;
        }
        store.markRunDone(runId, tenantId, clock.instant());
        return BatchJobStore.JobRunStatus.DONE;
    }
}
