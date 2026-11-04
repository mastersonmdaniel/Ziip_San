package br.com.ziipsan.platform.batch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P2.1: persistência das execuções de lote (core V10).
 *
 * <p>Uma execução é identificada por (tenant, ciclo). A trava contra
 * disparo duplo é a unicidade (tenant, ciclo) de {@code job_runs}:
 * {@link #createRun} lança {@link DuplicateJobRunException} quando a
 * execução já existe, e o chamador registra a recusa em
 * {@link #recordDispatch} — o segundo pedido nunca corre.
 *
 * <p>Retomada: rotas DONE nunca voltam; PENDING, FAILED e RUNNING
 * interrompida são executadas de novo. {@link #resetInterruptedRoutes}
 * devolve as RUNNING órfãs (processo morto no meio da rota) a PENDING
 * antes da retomada.
 */
public interface BatchJobStore {

    enum JobRunStatus { QUEUED, RUNNING, DONE, FAILED, INTERRUPTED }

    enum RouteStatus { PENDING, RUNNING, DONE, FAILED }

    enum DispatchOutcome { ACCEPTED, REFUSED_DUPLICATE }

    record JobRun(UUID id, UUID tenantId, String cycleRef, JobRunStatus status,
                  LocalDate referenceDate, String timezone, UUID triggeredBy,
                  Instant scheduledFor, Instant startedAt, Instant finishedAt) {
    }

    record JobRoute(UUID id, UUID runId, UUID tenantId, String route, int sequence,
                    RouteStatus status, int attempts, String lastError) {
    }

    record DispatchRecord(UUID id, UUID tenantId, String cycleRef, DispatchOutcome outcome,
                          UUID runId, UUID requestedBy, Instant requestedAt, String reason) {
    }

    /** Execução já existe para (tenant, ciclo): o segundo disparo é recusado. */
    final class DuplicateJobRunException extends RuntimeException {
        private final UUID existingRunId;

        public DuplicateJobRunException(UUID existingRunId) {
            super("Já existe uma execução de lote para este tenant e ciclo: " + existingRunId);
            this.existingRunId = existingRunId;
        }

        public UUID existingRunId() {
            return existingRunId;
        }
    }

    /**
     * Cria a execução QUEUED com as rotas PENDING, em ordem.
     *
     * @throws DuplicateJobRunException se (tenant, ciclo) já tem execução
     * @throws IllegalArgumentException se a lista de rotas estiver vazia ou repetir rota
     */
    JobRun createRun(UUID tenantId, String cycleRef, List<String> routes, LocalDate referenceDate,
                     String timezone, UUID triggeredBy, Instant scheduledFor);

    Optional<JobRun> findRun(UUID tenantId, String cycleRef);

    /**
     * Toda leitura/escrita por id recebe o tenant explícito: sob RLS
     * FORÇADA, a role da aplicação só enxerga a linha com o GUC do
     * tenant setado, e o store não tem como adivinhar o tenant a
     * partir do id. O serviço sempre tem o tenant no contexto.
     */
    JobRun getRun(UUID runId, UUID tenantId);

    /** Rotas da execução, em ordem de sequência. */
    List<JobRoute> routes(UUID runId, UUID tenantId);

    void markRunRunning(UUID runId, UUID tenantId, Instant startedAt);

    void markRunDone(UUID runId, UUID tenantId, Instant finishedAt);

    void markRunFailed(UUID runId, UUID tenantId, Instant finishedAt);

    void markRunInterrupted(UUID runId, UUID tenantId, Instant finishedAt);

    /** RUNNING órfãs voltam a PENDING; devolve quantas voltaram. */
    int resetInterruptedRoutes(UUID runId, UUID tenantId);

    void markRouteRunning(UUID routeId, UUID tenantId, Instant startedAt);

    void markRouteDone(UUID routeId, UUID tenantId, Instant finishedAt);

    /** FAILED com mais uma tentativa e o erro truncado pelo store. */
    void markRouteFailed(UUID routeId, UUID tenantId, String error, Instant finishedAt);

    DispatchRecord recordDispatch(UUID tenantId, String cycleRef, DispatchOutcome outcome,
                                  UUID runId, UUID requestedBy, String reason);

    List<DispatchRecord> dispatches(UUID tenantId, String cycleRef);
}
