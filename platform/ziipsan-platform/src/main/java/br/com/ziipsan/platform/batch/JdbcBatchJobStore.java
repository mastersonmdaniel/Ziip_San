package br.com.ziipsan.platform.batch;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * P2.1: {@link BatchJobStore} sobre as tabelas da core V10.
 *
 * <p>Contexto de tenant: cada método roda numa transação só, com
 * {@code set_config('app.current_tenant', ?, true)} — o mesmo padrão
 * dos stores JDBC da plataforma (ex.: TenantScopedConnection). O
 * contexto é local à transação e morre no commit/rollback, e o
 * autoCommit original é restaurado antes de devolver a conexão ao
 * pool: sem isso, uma conexão suja vazaria tenant para a próxima
 * requisição. A trava de disparo duplo é o UNIQUE (tenant, ciclo):
 * o INSERT que esbarra nele devolve SQLState 23505 e vira
 * {@link BatchJobStore.DuplicateJobRunException}.
 *
 * <p>Erros de rota são truncados em 4000 caracteres (o mesmo teto do
 * worker da outbox da P1.9): a coluna é TEXT, mas o registro de erro
 * não precisa guardar um stack trace inteiro.
 */
public final class JdbcBatchJobStore implements BatchJobStore {

    private static final int MAX_ERROR_CHARS = 4000;

    private final DataSource dataSource;

    public JdbcBatchJobStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório");
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T inTenantTransaction(UUID tenantId, Work<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public JobRun createRun(UUID tenantId, String cycleRef, List<String> routes,
                            LocalDate referenceDate, String timezone, UUID triggeredBy,
                            Instant scheduledFor) {
        validateNewRun(tenantId, cycleRef, routes, referenceDate, timezone);
        UUID runId = UUID.randomUUID();
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO public.job_runs (id, tenant_id, cycle_ref, status,"
                                + " reference_date, timezone, triggered_by, scheduled_for)"
                                + " VALUES (?, ?, ?, 'QUEUED', ?, ?, ?, ?)")) {
                    ps.setObject(1, runId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, cycleRef);
                    ps.setDate(4, Date.valueOf(referenceDate));
                    ps.setString(5, timezone);
                    setNullableUuid(ps, 6, triggeredBy);
                    setNullableTimestamp(ps, 7, scheduledFor);
                    ps.executeUpdate();
                }
                int sequence = 0;
                for (String route : routes) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO public.job_run_routes"
                                    + " (id, run_id, tenant_id, route, sequence, status)"
                                    + " VALUES (?, ?, ?, ?, ?, 'PENDING')")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, runId);
                        ps.setObject(3, tenantId);
                        ps.setString(4, route);
                        ps.setInt(5, sequence++);
                        ps.executeUpdate();
                    }
                }
                return new JobRun(runId, tenantId, cycleRef, JobRunStatus.QUEUED,
                        referenceDate, timezone, triggeredBy, scheduledFor, null, null);
            });
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                UUID existing = findRun(tenantId, cycleRef).map(JobRun::id).orElse(null);
                throw new DuplicateJobRunException(existing);
            }
            throw new IllegalStateException("Falha ao criar a execução de lote", e);
        }
    }

    @Override
    public Optional<JobRun> findRun(UUID tenantId, String cycleRef) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(cycleRef, "O ciclo é obrigatório");
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, tenant_id, cycle_ref, status, reference_date, timezone,"
                                + " triggered_by, scheduled_for, started_at, finished_at"
                                + " FROM public.job_runs WHERE tenant_id = ? AND cycle_ref = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, cycleRef);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? Optional.of(readRun(rs)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar a execução de lote", e);
        }
    }

    @Override
    public JobRun getRun(UUID runId, UUID tenantId) {
        Objects.requireNonNull(runId, "A execução é obrigatória");
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, tenant_id, cycle_ref, status, reference_date, timezone,"
                                + " triggered_by, scheduled_for, started_at, finished_at"
                                + " FROM public.job_runs WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, runId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Execução de lote inexistente: " + runId);
                        }
                        return readRun(rs);
                    }
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar a execução de lote", e);
        }
    }

    @Override
    public List<JobRoute> routes(UUID runId, UUID tenantId) {
        Objects.requireNonNull(runId, "A execução é obrigatória");
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, run_id, tenant_id, route, sequence, status, attempts, last_error"
                                + " FROM public.job_run_routes"
                                + " WHERE run_id = ? AND tenant_id = ? ORDER BY sequence")) {
                    ps.setObject(1, runId);
                    ps.setObject(2, tenantId);
                    List<JobRoute> routes = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            routes.add(readRoute(rs));
                        }
                    }
                    return routes;
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar as rotas da execução", e);
        }
    }

    @Override
    public void markRunRunning(UUID runId, UUID tenantId, Instant startedAt) {
        updateRun(runId, tenantId, "RUNNING", startedAt, null, true);
    }

    @Override
    public void markRunDone(UUID runId, UUID tenantId, Instant finishedAt) {
        updateRun(runId, tenantId, "DONE", null, finishedAt, false);
    }

    @Override
    public void markRunFailed(UUID runId, UUID tenantId, Instant finishedAt) {
        updateRun(runId, tenantId, "FAILED", null, finishedAt, false);
    }

    @Override
    public void markRunInterrupted(UUID runId, UUID tenantId, Instant finishedAt) {
        updateRun(runId, tenantId, "INTERRUPTED", null, finishedAt, false);
    }

    private void updateRun(UUID runId, UUID tenantId, String status, Instant startedAt,
                           Instant finishedAt, boolean setStarted) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        try {
            inTenantTransaction(tenantId, connection -> {
                String sql = setStarted
                        ? "UPDATE public.job_runs SET status = ?, started_at = ?,"
                                + " updated_at = NOW() WHERE id = ? AND tenant_id = ?"
                        : "UPDATE public.job_runs SET status = ?, finished_at = ?,"
                                + " updated_at = NOW() WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, status);
                    ps.setTimestamp(2, Timestamp.from(setStarted ? startedAt : finishedAt));
                    ps.setObject(3, runId);
                    ps.setObject(4, tenantId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Execução de lote inexistente: " + runId);
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao atualizar a execução de lote", e);
        }
    }

    @Override
    public int resetInterruptedRoutes(UUID runId, UUID tenantId) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE public.job_run_routes SET status = 'PENDING',"
                                + " updated_at = NOW() WHERE run_id = ? AND tenant_id = ?"
                                + " AND status = 'RUNNING'")) {
                    ps.setObject(1, runId);
                    ps.setObject(2, tenantId);
                    return ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao resetar as rotas interrompidas", e);
        }
    }

    @Override
    public void markRouteRunning(UUID routeId, UUID tenantId, Instant startedAt) {
        updateRouteStatus(routeId, tenantId, "RUNNING", startedAt, null, null, false);
    }

    @Override
    public void markRouteDone(UUID routeId, UUID tenantId, Instant finishedAt) {
        updateRouteStatus(routeId, tenantId, "DONE", null, finishedAt, null, false);
    }

    @Override
    public void markRouteFailed(UUID routeId, UUID tenantId, String error, Instant finishedAt) {
        String truncated = error == null ? null
                : error.substring(0, Math.min(error.length(), MAX_ERROR_CHARS));
        updateRouteStatus(routeId, tenantId, "FAILED", null, finishedAt, truncated, true);
    }

    private void updateRouteStatus(UUID routeId, UUID tenantId, String status, Instant startedAt,
                                   Instant finishedAt, String error, boolean incrementAttempts) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        try {
            inTenantTransaction(tenantId, connection -> {
                String sql;
                if (startedAt != null) {
                    sql = "UPDATE public.job_run_routes SET status = ?, started_at = ?,"
                            + " updated_at = NOW() WHERE id = ? AND tenant_id = ?";
                } else if (incrementAttempts) {
                    sql = "UPDATE public.job_run_routes SET status = ?, finished_at = ?,"
                            + " last_error = ?, attempts = attempts + 1,"
                            + " updated_at = NOW() WHERE id = ? AND tenant_id = ?";
                } else {
                    sql = "UPDATE public.job_run_routes SET status = ?, finished_at = ?,"
                            + " updated_at = NOW() WHERE id = ? AND tenant_id = ?";
                }
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, status);
                    if (startedAt != null) {
                        ps.setTimestamp(2, Timestamp.from(startedAt));
                        ps.setObject(3, routeId);
                        ps.setObject(4, tenantId);
                    } else if (incrementAttempts) {
                        ps.setTimestamp(2, Timestamp.from(finishedAt));
                        ps.setString(3, error);
                        ps.setObject(4, routeId);
                        ps.setObject(5, tenantId);
                    } else {
                        ps.setTimestamp(2, Timestamp.from(finishedAt));
                        ps.setObject(3, routeId);
                        ps.setObject(4, tenantId);
                    }
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Rota de lote inexistente: " + routeId);
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao atualizar a rota do lote", e);
        }
    }


    @Override
    public DispatchRecord recordDispatch(UUID tenantId, String cycleRef, DispatchOutcome outcome,
                                         UUID runId, UUID requestedBy, String reason) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(cycleRef, "O ciclo é obrigatório");
        Objects.requireNonNull(outcome, "O resultado do disparo é obrigatório");
        UUID id = UUID.randomUUID();
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO public.job_run_dispatches"
                                + " (id, tenant_id, cycle_ref, outcome, run_id, requested_by, reason)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setString(3, cycleRef);
                    ps.setString(4, outcome.name());
                    setNullableUuid(ps, 5, runId);
                    setNullableUuid(ps, 6, requestedBy);
                    ps.setString(7, reason);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT requested_at FROM public.job_run_dispatches WHERE id = ?")) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return new DispatchRecord(id, tenantId, cycleRef, outcome, runId,
                                requestedBy, toInstant(rs.getTimestamp("requested_at")), reason);
                    }
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar o disparo do lote", e);
        }
    }

    @Override
    public List<DispatchRecord> dispatches(UUID tenantId, String cycleRef) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(cycleRef, "O ciclo é obrigatório");
        try {
            return inTenantTransaction(tenantId, connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, tenant_id, cycle_ref, outcome, run_id, requested_by,"
                                + " requested_at, reason FROM public.job_run_dispatches"
                                + " WHERE tenant_id = ? AND cycle_ref = ? ORDER BY requested_at")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, cycleRef);
                    List<DispatchRecord> records = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            records.add(new DispatchRecord(
                                    (UUID) rs.getObject("id"),
                                    (UUID) rs.getObject("tenant_id"),
                                    rs.getString("cycle_ref"),
                                    DispatchOutcome.valueOf(rs.getString("outcome")),
                                    (UUID) rs.getObject("run_id"),
                                    (UUID) rs.getObject("requested_by"),
                                    toInstant(rs.getTimestamp("requested_at")),
                                    rs.getString("reason")));
                        }
                    }
                    return records;
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao listar os disparos do lote", e);
        }
    }

    private static void validateNewRun(UUID tenantId, String cycleRef, List<String> routes,
                                       LocalDate referenceDate, String timezone) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(cycleRef, "O ciclo é obrigatório");
        Objects.requireNonNull(routes, "As rotas são obrigatórias");
        Objects.requireNonNull(referenceDate, "A data de referência é obrigatória");
        Objects.requireNonNull(timezone, "O fuso horário é obrigatório");
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("A execução precisa de pelo menos uma rota");
        }
        Set<String> seen = new HashSet<>();
        for (String route : routes) {
            if (route == null || route.isBlank() || !seen.add(route)) {
                throw new IllegalArgumentException(
                        "Rotas inválidas ou repetidas na execução: " + routes);
            }
        }
    }

    private static JobRun readRun(ResultSet rs) throws SQLException {
        return new JobRun(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("cycle_ref"),
                JobRunStatus.valueOf(rs.getString("status")),
                rs.getDate("reference_date").toLocalDate(),
                rs.getString("timezone"),
                (UUID) rs.getObject("triggered_by"),
                toInstant(rs.getTimestamp("scheduled_for")),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at")));
    }

    private static JobRoute readRoute(ResultSet rs) throws SQLException {
        return new JobRoute(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("run_id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("route"),
                rs.getInt("sequence"),
                RouteStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getString("last_error"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setNullableUuid(PreparedStatement ps, int index, UUID value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, Instant value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }
}
