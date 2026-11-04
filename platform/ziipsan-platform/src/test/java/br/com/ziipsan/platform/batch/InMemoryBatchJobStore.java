package br.com.ziipsan.platform.batch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P2.1: {@link BatchJobStore} em memória, só para testes. Replica as
 * regras do store JDBC que os testes de serviço precisam provar sem
 * banco: unicidade (tenant, ciclo), ordem das rotas, DONE nunca
 * reprocessado e reset de RUNNING órfã. Não é implementação de
 * produção.
 */
final class InMemoryBatchJobStore implements BatchJobStore {

    private final Map<UUID, JobRun> runs = new LinkedHashMap<>();
    private final Map<String, UUID> byTenantCycle = new LinkedHashMap<>();
    private final Map<UUID, List<JobRoute>> routesByRun = new LinkedHashMap<>();
    private final Map<UUID, JobRoute> routesById = new LinkedHashMap<>();
    private final List<DispatchRecord> dispatchRecords = new ArrayList<>();

    private synchronized JobRun putRun(JobRun run) {
        runs.put(run.id(), run);
        return run;
    }

    private synchronized JobRoute putRoute(JobRoute route) {
        routesById.put(route.id(), route);
        routesByRun.get(route.runId()).removeIf(r -> r.id().equals(route.id()));
        routesByRun.get(route.runId()).add(route);
        routesByRun.get(route.runId()).sort((a, b) -> Integer.compare(a.sequence(), b.sequence()));
        return route;
    }

    @Override
    public synchronized JobRun createRun(UUID tenantId, String cycleRef, List<String> routes,
                                         LocalDate referenceDate, String timezone,
                                         UUID triggeredBy, Instant scheduledFor) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(cycleRef, "O ciclo é obrigatório");
        Objects.requireNonNull(routes, "As rotas são obrigatórias");
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("A execução precisa de pelo menos uma rota");
        }
        if (routes.stream().distinct().count() != routes.size()) {
            throw new IllegalArgumentException("Rotas repetidas na execução: " + routes);
        }
        String key = tenantId + "|" + cycleRef;
        if (byTenantCycle.containsKey(key)) {
            throw new DuplicateJobRunException(byTenantCycle.get(key));
        }
        UUID runId = UUID.randomUUID();
        JobRun run = new JobRun(runId, tenantId, cycleRef, JobRunStatus.QUEUED,
                referenceDate, timezone, triggeredBy, scheduledFor, null, null);
        runs.put(runId, run);
        byTenantCycle.put(key, runId);
        List<JobRoute> created = new ArrayList<>();
        int sequence = 0;
        for (String route : routes) {
            JobRoute r = new JobRoute(UUID.randomUUID(), runId, tenantId, route, sequence++,
                    RouteStatus.PENDING, 0, null);
            created.add(r);
            routesById.put(r.id(), r);
        }
        routesByRun.put(runId, created);
        return run;
    }

    @Override
    public synchronized Optional<JobRun> findRun(UUID tenantId, String cycleRef) {
        UUID id = byTenantCycle.get(tenantId + "|" + cycleRef);
        return id == null ? Optional.empty() : Optional.of(runs.get(id));
    }

    @Override
    public synchronized JobRun getRun(UUID runId, UUID tenantId) {
        JobRun run = runs.get(runId);
        if (run == null || !run.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Execução de lote inexistente: " + runId);
        }
        return run;
    }

    @Override
    public synchronized List<JobRoute> routes(UUID runId, UUID tenantId) {
        getRun(runId, tenantId);
        return List.copyOf(routesByRun.getOrDefault(runId, List.of()));
    }

    private synchronized JobRun setRunStatus(UUID runId, UUID tenantId, JobRunStatus status,
                                             Instant startedAt, Instant finishedAt) {
        JobRun run = getRun(runId, tenantId);
        JobRun updated = new JobRun(run.id(), run.tenantId(), run.cycleRef(), status,
                run.referenceDate(), run.timezone(), run.triggeredBy(), run.scheduledFor(),
                startedAt != null ? startedAt : run.startedAt(),
                finishedAt != null ? finishedAt : run.finishedAt());
        return putRun(updated);
    }

    @Override
    public void markRunRunning(UUID runId, UUID tenantId, Instant startedAt) {
        setRunStatus(runId, tenantId, JobRunStatus.RUNNING, startedAt, null);
    }

    @Override
    public void markRunDone(UUID runId, UUID tenantId, Instant finishedAt) {
        setRunStatus(runId, tenantId, JobRunStatus.DONE, null, finishedAt);
    }

    @Override
    public void markRunFailed(UUID runId, UUID tenantId, Instant finishedAt) {
        setRunStatus(runId, tenantId, JobRunStatus.FAILED, null, finishedAt);
    }

    @Override
    public void markRunInterrupted(UUID runId, UUID tenantId, Instant finishedAt) {
        setRunStatus(runId, tenantId, JobRunStatus.INTERRUPTED, null, finishedAt);
    }

    @Override
    public synchronized int resetInterruptedRoutes(UUID runId, UUID tenantId) {
        getRun(runId, tenantId);
        int reset = 0;
        for (JobRoute route : List.copyOf(routesByRun.getOrDefault(runId, List.of()))) {
            if (route.status() == RouteStatus.RUNNING) {
                putRoute(new JobRoute(route.id(), route.runId(), route.tenantId(), route.route(),
                        route.sequence(), RouteStatus.PENDING, route.attempts(), route.lastError()));
                reset++;
            }
        }
        return reset;
    }

    private synchronized void setRouteStatus(UUID routeId, UUID tenantId, RouteStatus status,
                                             String error, boolean incrementAttempts) {
        JobRoute route = routesById.get(routeId);
        if (route == null || !route.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Rota de lote inexistente: " + routeId);
        }
        putRoute(new JobRoute(route.id(), route.runId(), route.tenantId(), route.route(),
                route.sequence(), status,
                incrementAttempts ? route.attempts() + 1 : route.attempts(),
                error != null ? error.substring(0, Math.min(error.length(), 4000)) : route.lastError()));
    }

    @Override
    public void markRouteRunning(UUID routeId, UUID tenantId, Instant startedAt) {
        setRouteStatus(routeId, tenantId, RouteStatus.RUNNING, null, false);
    }

    @Override
    public void markRouteDone(UUID routeId, UUID tenantId, Instant finishedAt) {
        setRouteStatus(routeId, tenantId, RouteStatus.DONE, null, false);
    }

    @Override
    public void markRouteFailed(UUID routeId, UUID tenantId, String error, Instant finishedAt) {
        setRouteStatus(routeId, tenantId, RouteStatus.FAILED, error, true);
    }

    @Override
    public synchronized DispatchRecord recordDispatch(UUID tenantId, String cycleRef,
                                                      DispatchOutcome outcome, UUID runId,
                                                      UUID requestedBy, String reason) {
        DispatchRecord record = new DispatchRecord(UUID.randomUUID(), tenantId, cycleRef, outcome,
                runId, requestedBy, Instant.now(), reason);
        dispatchRecords.add(record);
        return record;
    }

    @Override
    public synchronized List<DispatchRecord> dispatches(UUID tenantId, String cycleRef) {
        return dispatchRecords.stream()
                .filter(d -> d.tenantId().equals(tenantId) && d.cycleRef().equals(cycleRef))
                .toList();
    }
}
