package br.com.ziipsan.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ziipsan.core.parameters.TenantTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.1 sobre o Postgres real: a V10 cria as tabelas, o store JDBC
 * persiste execuções/rotas/disparos com a trava (tenant, ciclo), a
 * RLS FORÇADA isola tenants na role da aplicação, e cada rota corre
 * como um Job Spring Batch com linhas nas tabelas BATCH_*.
 *
 * <p>Cada teste usa tenant e ciclo próprios, para o lote de um teste
 * nunca tocar no de outro na mesma JVM/banco.
 */
class BatchJobStoreIT {

    private static final Clock FIXED = Clock.fixed(
            Instant.parse("2027-02-01T12:00:00Z"), ZoneOffset.UTC);

    @BeforeAll
    static void ensureDatabase() {
        BatchItDatabase.ensure();
    }

    @Test
    @DisplayName("V10: tabelas de lote e do Batch existem após as migrations de core")
    void v10TablesExist() throws Exception {
        try (Connection conn = BatchItDatabase.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables"
                             + " WHERE table_schema = 'public' AND table_name IN"
                             + " ('job_runs', 'job_run_routes', 'job_run_dispatches',"
                             + " 'batch_job_instance', 'batch_job_execution')")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(5);
            }
        }
    }

    @Test
    @DisplayName("store JDBC: cria, trava duplicata, registra recusa e isola tenants por RLS")
    void jdbcStoreRoundTripWithLockAndRls() throws Exception {
        JdbcBatchJobStore store = new JdbcBatchJobStore(BatchItDatabase.workerDs);
        UUID tenant = UUID.randomUUID();
        String cycle = "2027-02-" + UUID.randomUUID().toString().substring(0, 8);
        BatchJobStore.JobRun run = store.createRun(tenant, cycle, List.of("rota-a", "rota-b"),
                LocalDate.of(2027, 2, 1), "America/Sao_Paulo", null, null);
        assertThat(run.status()).isEqualTo(BatchJobStore.JobRunStatus.QUEUED);
        assertThat(store.routes(run.id(), tenant)).hasSize(2);
        assertThatThrownBy(() -> store.createRun(tenant, cycle, List.of("rota-a"),
                LocalDate.of(2027, 2, 1), "America/Sao_Paulo", null, null))
                .isInstanceOf(BatchJobStore.DuplicateJobRunException.class)
                .extracting(e -> ((BatchJobStore.DuplicateJobRunException) e).existingRunId())
                .isEqualTo(run.id());
        store.recordDispatch(tenant, cycle, BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE,
                run.id(), null, "duplicata");
        assertThat(store.dispatches(tenant, cycle)).hasSize(1);
        assertThat(store.dispatches(tenant, cycle).get(0).outcome())
                .isEqualTo(BatchJobStore.DispatchOutcome.REFUSED_DUPLICATE);

        // RLS: outro tenant, na mesma conexão com outro GUC, não vê a execução.
        UUID other = UUID.randomUUID();
        assertThat(store.findRun(other, cycle)).isEmpty();
        assertThatThrownBy(() -> store.getRun(run.id(), other))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("serviço + Batch real: rota corre como Job, falha aborta e retomada conclui")
    void serviceWithRealSpringBatch() throws Exception {
        JdbcBatchJobStore store = new JdbcBatchJobStore(BatchItDatabase.workerDs);
        SpringBatchRouteExecutor executor =
                new SpringBatchRouteExecutor(BatchItDatabase.workerDs);
        AtomicInteger executions = new AtomicInteger();
        Map<String, RouteHandler> handlers = Map.of(
                "rota-a", ctx -> executions.incrementAndGet(),
                "rota-b", ctx -> {
                    if (executions.incrementAndGet() == 2) {
                        throw new RuntimeException("falha simulada da rota-b");
                    }
                });
        UUID tenant = UUID.randomUUID();
        String cycle = "2027-02-" + UUID.randomUUID().toString().substring(0, 8);
        String batchCountSql = "SELECT COUNT(*) FROM batch_job_execution";
        int batchBefore = count(batchCountSql);
        try (FairBatchDispatcher dispatcher = new FairBatchDispatcher(2)) {
            BatchJobService svc = new BatchJobService(store, executor, dispatcher, handlers, FIXED);
            BatchJobService.DispatchResult first = svc.dispatch(tenant, cycle,
                    List.of("rota-a", "rota-b"), TenantTime.of("America/Sao_Paulo", FIXED),
                    null, null);
            assertThat(first.outcome()).isEqualTo(BatchJobStore.DispatchOutcome.ACCEPTED);
            assertThat(first.completion().get(60, TimeUnit.SECONDS))
                    .isEqualTo(BatchJobStore.JobRunStatus.FAILED);
            assertThat(count(batchCountSql)).isGreaterThan(batchBefore);
            assertThat(store.routes(first.runId(), tenant).stream()
                    .map(r -> r.route() + "=" + r.status()).toList())
                    .containsExactly("rota-a=DONE", "rota-b=FAILED");
            BatchJobService.ResumeResult resumed = svc.resume(first.runId(), tenant);
            assertThat(resumed.resumed()).isTrue();
            assertThat(resumed.completion().get(60, TimeUnit.SECONDS))
                    .isEqualTo(BatchJobStore.JobRunStatus.DONE);
            assertThat(executions.get()).isEqualTo(3);
        }
    }

    private static int count(String sql) throws Exception {
        try (Connection conn = BatchItDatabase.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
