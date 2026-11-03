package br.com.ziipsan.platform.infra.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * P1.9: worker básico da outbox (ADR-0014). Processo {@code worker} (perfil
 * worker da aplicação) com o DataSource da role {@code ziipsan_worker} —
 * a única role que lê linhas de todos os tenants, e só nesta tabela.
 *
 * <p>Tomada: {@code FOR UPDATE SKIP LOCKED} com prazo de posse
 * ({@code locked_until}). Posse vencida volta à fila e é retomada por outro
 * worker; a marcação de conclusão exige o mesmo {@code locked_until} tomado,
 * então um worker lento não marca como concluída uma mensagem que outro
 * worker já retomou. Entrega ao menos uma vez; handlers idempotentes.
 *
 * <p>Tenant: cada mensagem carrega o tenant explícito e é entregue com ele
 * ao handler. O worker NUNCA escreve no {@code TenantContextHolder} em
 * ThreadLocal: handlers executam sem tenant ambiente, e qualquer handler
 * que dependa do ThreadLocal vê vazio (testado). Mensagens de tenants
 * diferentes no mesmo lote não se misturam: cada entrega leva o seu tenant.
 *
 * <p>Falha: nova tentativa com backoff exponencial + jitter, até
 * {@code maxAttempts}; depois, estado DEAD com {@code last_error}, visível
 * no console com reprocessamento (console da P1.6). Tipo sem handler conta
 * como falha, pelo mesmo caminho — nunca some em silêncio.
 *
 * <p>Fila justa por tenant (ADR-0014 item 4, com métricas e autoscaling) é
 * da P2: aqui a tomada ordena por disponibilidade e antiguidade, sem
 * reserva por tenant. O filtro de fila ({@code queueFilter}, null = todas)
 * existe para isolar filas e para os testes.
 */
public final class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    /** Posse padrão de 5 minutos: worker morto tem a mensagem retomada sem espera longa. */
    public static final Duration DEFAULT_LEASE = Duration.ofMinutes(5);
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_BASE_BACKOFF = Duration.ofSeconds(60);
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofHours(1);
    public static final int DEFAULT_BATCH_SIZE = 10;
    private static final int MAX_ERROR_CHARS = 4000;

    private final DataSource dataSource;
    private final Map<String, MessageHandler> handlers;
    private final Clock clock;
    private final Duration lease;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final int batchSize;
    private final String queueFilter;

    private volatile boolean running;
    private Thread loopThread;

    public OutboxWorker(DataSource dataSource, Map<String, MessageHandler> handlers) {
        this(dataSource, handlers, Clock.systemUTC(), DEFAULT_LEASE, DEFAULT_MAX_ATTEMPTS,
                DEFAULT_BASE_BACKOFF, DEFAULT_MAX_BACKOFF, DEFAULT_BATCH_SIZE, null);
    }

    public OutboxWorker(DataSource dataSource, Map<String, MessageHandler> handlers, Clock clock,
                        Duration lease, int maxAttempts, Duration baseBackoff, Duration maxBackoff,
                        int batchSize, String queueFilter) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource obrigatorio");
        }
        if (handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("ao menos um handler e obrigatorio");
        }
        if (clock == null || lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("clock e lease positivo sao obrigatorios");
        }
        if (maxAttempts < 1 || batchSize < 1 || baseBackoff == null || baseBackoff.isNegative()
                || maxBackoff == null || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("parametros do worker invalidos");
        }
        this.dataSource = dataSource;
        this.handlers = new HashMap<>(handlers);
        this.clock = clock;
        this.lease = lease;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
        this.batchSize = batchSize;
        this.queueFilter = queueFilter;
    }

    /**
     * Toma um lote, entrega cada mensagem ao handler do tipo e marca o
     * resultado. Retorna quantas mensagens foram tomadas (0 = fila vazia).
     */
    public int processBatch() {
        List<Claimed> claimed = claim();
        for (Claimed message : claimed) {
            MessageHandler handler = handlers.get(message.message().type());
            try {
                if (handler == null) {
                    throw new IllegalStateException("sem handler para o tipo " + message.message().type());
                }
                handler.handle(message.message());
                markDone(message);
            } catch (Exception e) {
                log.warn("outbox {} falhou (tentativa {}): {}", message.message().id(),
                        message.message().attempts(), e.toString());
                markFailed(message, e);
            }
        }
        return claimed.size();
    }

    /** Laço de polling em thread daemon; {@link #stop()} interrompe o sono entre lotes. */
    public synchronized void start(Duration pollInterval) {
        if (running) {
            throw new IllegalStateException("worker ja em execucao");
        }
        if (pollInterval == null || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval obrigatorio");
        }
        running = true;
        loopThread = new Thread(() -> {
            while (running) {
                try {
                    processBatch();
                } catch (RuntimeException e) {
                    log.warn("lote da outbox falhou", e);
                }
                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ziipsan-outbox-worker");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (loopThread != null) {
            loopThread.interrupt();
        }
    }

    private List<Claimed> claim() {
        String sql = "UPDATE public.outbox o"
                + " SET status = 'PROCESSING', locked_until = ?, attempts = o.attempts + 1, updated_at = NOW()"
                + " FROM (SELECT id FROM public.outbox WHERE available_at <= NOW()"
                + " AND (status = 'PENDING'"
                + " OR (status = 'PROCESSING' AND (locked_until IS NULL OR locked_until < NOW())))"
                + (queueFilter == null ? "" : " AND queue = ?")
                + " ORDER BY available_at ASC, created_at ASC, id ASC LIMIT ? FOR UPDATE SKIP LOCKED) c"
                + " WHERE o.id = c.id"
                + " RETURNING o.id, o.tenant_id, o.queue, o.type, o.payload::text, o.attempts, o.locked_until";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setTimestamp(i++, Timestamp.from(clock.instant().plus(lease)));
                if (queueFilter != null) {
                    ps.setString(i++, queueFilter);
                }
                ps.setInt(i, batchSize);
                List<Claimed> claimed = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        var message = new OutboxMessage(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getInt(6));
                        claimed.add(new Claimed(message, rs.getTimestamp(7).toInstant()));
                    }
                }
                connection.commit();
                return claimed;
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("falha ao tomar lote da outbox", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao tomar lote da outbox", e);
        }
    }

    private void markDone(Claimed claimed) {
        executeMark("UPDATE public.outbox SET status = 'DONE', locked_until = NULL, updated_at = NOW()"
                        + " WHERE id = ? AND status = 'PROCESSING' AND locked_until = ?",
                claimed, null, null, null);
    }

    private void markFailed(Claimed claimed, Exception failure) {
        int attempts = claimed.message().attempts();
        boolean dead = attempts >= maxAttempts;
        String error = failure.toString();
        if (error.length() > MAX_ERROR_CHARS) {
            error = error.substring(0, MAX_ERROR_CHARS);
        }
        Instant availableAt = dead ? clock.instant() : clock.instant().plus(backoffDelay(attempts));
        executeMark("UPDATE public.outbox SET status = ?, available_at = ?, locked_until = NULL,"
                        + " last_error = ?, updated_at = NOW()"
                        + " WHERE id = ? AND status = 'PROCESSING' AND locked_until = ?",
                claimed, dead ? "DEAD" : "PENDING", availableAt, error);
    }

    private void executeMark(String sql, Claimed claimed, String status, Instant availableAt, String error) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                if (status != null) {
                    ps.setString(i++, status);
                    ps.setTimestamp(i++, Timestamp.from(availableAt));
                    ps.setString(i++, error);
                }
                ps.setObject(i++, claimed.message().id());
                ps.setTimestamp(i, Timestamp.from(claimed.lockedUntil()));
                ps.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("falha ao marcar mensagem da outbox", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao marcar mensagem da outbox", e);
        }
    }

    private Duration backoffDelay(int attempts) {
        long multiplier = 1L << Math.min(attempts - 1, 30);
        Duration exponential;
        try {
            exponential = baseBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            exponential = maxBackoff;
        }
        if (exponential.compareTo(maxBackoff) > 0) {
            exponential = maxBackoff;
        }
        long jitterMillis = baseBackoff.isZero() ? 0
                : ThreadLocalRandom.current().nextLong(baseBackoff.toMillis() + 1);
        return exponential.plusMillis(jitterMillis);
    }

    private record Claimed(OutboxMessage message, Instant lockedUntil) {
    }
}
