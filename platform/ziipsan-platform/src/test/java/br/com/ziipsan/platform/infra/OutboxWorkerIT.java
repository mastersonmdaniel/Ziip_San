package br.com.ziipsan.platform.infra;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.infra.queue.MessageHandler;
import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.queue.OutboxMessage;
import br.com.ziipsan.platform.infra.queue.OutboxWorker;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.9: worker básico sobre a outbox real — entrega com tenant explícito,
 * sem ThreadLocal, SKIP LOCKED com posse, backoff e DEAD.
 *
 * <p>Cada teste publica na sua própria fila (nome único), para o lote de um
 * teste nunca tomar mensagens de outro: os testes dividem a JVM e o banco.
 */
class OutboxWorkerIT {

    private static final String TYPE = "billing.invoice.issued";

    @BeforeAll
    static void ensureDatabase() {
        OutboxItDatabase.ensure();
    }

    private record Delivery(UUID tenantId, String payloadJson, UUID threadLocalTenant) {
    }

    private void publish(String queue, MessageQueue.QueuedMessage message) throws Exception {
        try (Connection connection = OutboxItDatabase.appConnection()) {
            new PostgresMessageQueue(new SingleConnectionDataSource(connection, true), queue)
                    .publish(message);
        }
    }

    private OutboxWorker worker(String queue, Map<String, MessageHandler> handlers,
                                Duration lease, int maxAttempts) {
        return new OutboxWorker(OutboxItDatabase.workerDs, handlers, Clock.systemUTC(),
                lease, maxAttempts, Duration.ofSeconds(60), Duration.ofSeconds(60), 10, queue);
    }

    private void expireLease(UUID id) throws Exception {
        try (Connection connection = OutboxItDatabase.workerConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE public.outbox SET locked_until = NOW() - INTERVAL '1 minute' WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private void forceAvailable(UUID id) throws Exception {
        try (Connection connection = OutboxItDatabase.workerConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE public.outbox SET available_at = NOW() - INTERVAL '1 minute' WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("lote entrega cada mensagem com o seu tenant, sem ThreadLocal e sem mistura")
    void batchDeliversEachMessageWithItsOwnTenant() throws Exception {
        String queue = OutboxItDatabase.queueName();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var msgA = new MessageQueue.QueuedMessage(UUID.randomUUID(), tenantA, TYPE, "{\"id\":\"A\"}");
        var msgB = new MessageQueue.QueuedMessage(UUID.randomUUID(), tenantB, TYPE, "{\"id\":\"B\"}");
        publish(queue, msgA);
        publish(queue, msgB);

        List<Delivery> seen = new ArrayList<>();
        MessageHandler recording = new MessageHandler() {
            @Override
            public String handledType() {
                return TYPE;
            }

            @Override
            public void handle(OutboxMessage message) {
                seen.add(new Delivery(message.tenantId(), message.payloadJson(),
                        TenantContextHolder.getTenantId()));
            }
        };
        int claimed = worker(queue, Map.of(TYPE, recording), Duration.ofMinutes(5), 5).processBatch();

        assertThat(claimed).isEqualTo(2);
        // payloadJson volta na forma normalizada do jsonb (espaco apos ':'), nao na string original.
        assertThat(seen).containsExactlyInAnyOrder(
                new Delivery(tenantA, "{\"id\": \"A\"}", null),
                new Delivery(tenantB, "{\"id\": \"B\"}", null));
        try (Connection connection = OutboxItDatabase.workerConnection()) {
            assertThat(OutboxItDatabase.statusOf(connection, msgA.messageId())).isEqualTo("DONE");
            assertThat(OutboxItDatabase.statusOf(connection, msgB.messageId())).isEqualTo("DONE");
        }
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("tarefa assincrona sem tenant explicito falha na construcao")
    void asyncTaskWithoutExplicitTenantFails() {
        assertThatThrownBy(() -> new OutboxMessage(UUID.randomUUID(), null, "default", TYPE, "{}", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("posse valida nao e retomada; posse vencida e retomada por outro worker")
    void validLeaseNotReclaimed_expiredLeaseReclaimed() throws Exception {
        String queue = OutboxItDatabase.queueName();
        var msg = new MessageQueue.QueuedMessage(UUID.randomUUID(), UUID.randomUUID(), TYPE, "{\"id\":1}");
        publish(queue, msg);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MessageHandler blocking = new MessageHandler() {
            @Override
            public String handledType() {
                return TYPE;
            }

            @Override
            public void handle(OutboxMessage message) throws Exception {
                entered.countDown();
                if (!release.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("handler bloqueado alem do limite");
                }
            }
        };
        List<UUID> secondWorkerSeen = new ArrayList<>();
        MessageHandler recording = new MessageHandler() {
            @Override
            public String handledType() {
                return TYPE;
            }

            @Override
            public void handle(OutboxMessage message) {
                secondWorkerSeen.add(message.tenantId());
            }
        };
        OutboxWorker first = worker(queue, Map.of(TYPE, blocking), Duration.ofHours(1), 5);
        OutboxWorker second = worker(queue, Map.of(TYPE, recording), Duration.ofMinutes(5), 5);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> firstBatch = executor.submit(first::processBatch);
            assertThat(entered.await(60, TimeUnit.SECONDS)).isTrue();
            try (Connection connection = OutboxItDatabase.workerConnection()) {
                assertThat(OutboxItDatabase.statusOf(connection, msg.messageId())).isEqualTo("PROCESSING");
            }
            // Posse válida: o segundo worker não toma nada (SKIP LOCKED + lease).
            assertThat(second.processBatch()).isZero();
            // Posse vencida: o segundo worker retoma e conclui.
            expireLease(msg.messageId());
            assertThat(second.processBatch()).isEqualTo(1);
            assertThat(secondWorkerSeen).containsExactly(msg.tenantId());
            release.countDown();
            assertThat(firstBatch.get(60, TimeUnit.SECONDS)).isEqualTo(1);
            try (Connection connection = OutboxItDatabase.workerConnection()) {
                assertThat(OutboxItDatabase.statusOf(connection, msg.messageId())).isEqualTo("DONE");
                // Tomada 1 (primeiro worker) + retomada (segundo worker).
                assertThat(OutboxItDatabase.attemptsOf(connection, msg.messageId())).isEqualTo(2);
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("falha volta com backoff e, no limite, vira DEAD com last_error")
    void failureBacksOffThenDead() throws Exception {
        String queue = OutboxItDatabase.queueName();
        var msg = new MessageQueue.QueuedMessage(UUID.randomUUID(), UUID.randomUUID(), TYPE, "{\"id\":1}");
        publish(queue, msg);
        MessageHandler failing = new MessageHandler() {
            @Override
            public String handledType() {
                return TYPE;
            }

            @Override
            public void handle(OutboxMessage message) {
                throw new RuntimeException("boom-falha-sintetica");
            }
        };
        OutboxWorker worker = worker(queue, Map.of(TYPE, failing), Duration.ofMinutes(5), 2);
        assertThat(worker.processBatch()).isEqualTo(1);
        try (Connection connection = OutboxItDatabase.workerConnection()) {
            assertThat(OutboxItDatabase.statusOf(connection, msg.messageId())).isEqualTo("PENDING");
            assertThat(OutboxItDatabase.attemptsOf(connection, msg.messageId())).isEqualTo(1);
            assertThat(OutboxItDatabase.lastErrorOf(connection, msg.messageId()))
                    .contains("boom-falha-sintetica");
        }
        // Backoff empurrou available_at para o futuro: sem forçar, nada é tomado.
        assertThat(worker.processBatch()).isZero();
        forceAvailable(msg.messageId());
        assertThat(worker.processBatch()).isEqualTo(1);
        try (Connection connection = OutboxItDatabase.workerConnection()) {
            assertThat(OutboxItDatabase.statusOf(connection, msg.messageId())).isEqualTo("DEAD");
            assertThat(OutboxItDatabase.attemptsOf(connection, msg.messageId())).isEqualTo(2);
            assertThat(OutboxItDatabase.lastErrorOf(connection, msg.messageId()))
                    .contains("boom-falha-sintetica");
        }
    }

    @Test
    @DisplayName("tipo sem handler vira DEAD no limite, com erro claro")
    void unknownTypeGoesDead() throws Exception {
        String queue = OutboxItDatabase.queueName();
        var msg = new MessageQueue.QueuedMessage(UUID.randomUUID(), UUID.randomUUID(),
                "unknown.type", "{\"id\":1}");
        publish(queue, msg);
        MessageHandler other = new MessageHandler() {
            @Override
            public String handledType() {
                return TYPE;
            }

            @Override
            public void handle(OutboxMessage message) {
            }
        };
        OutboxWorker worker = worker(queue, Map.of(TYPE, other), Duration.ofMinutes(5), 1);
        assertThat(worker.processBatch()).isEqualTo(1);
        try (Connection connection = OutboxItDatabase.workerConnection()) {
            assertThat(OutboxItDatabase.statusOf(connection, msg.messageId())).isEqualTo("DEAD");
            assertThat(OutboxItDatabase.lastErrorOf(connection, msg.messageId()))
                    .contains("sem handler");
        }
    }
}
