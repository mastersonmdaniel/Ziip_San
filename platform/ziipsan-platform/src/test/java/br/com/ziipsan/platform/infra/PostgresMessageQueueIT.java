package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.9: comportamento do publicador sobre a outbox real — deduplicação,
 * transação, RLS e partição diária. (O contrato em si está na suíte
 * herdada, em {@link PostgresMessageQueueContractIT}.)
 */
class PostgresMessageQueueIT {

    private static final String TYPE = "billing.invoice.issued";

    @BeforeAll
    static void ensureDatabase() {
        OutboxItDatabase.ensure();
    }

    private MessageQueue.QueuedMessage message(UUID tenantId) {
        return new MessageQueue.QueuedMessage(UUID.randomUUID(), tenantId, TYPE, "{\"id\":1}");
    }

    private PostgresMessageQueue queueOn(Connection connection) {
        return new PostgresMessageQueue(new SingleConnectionDataSource(connection, true));
    }

    private int countInTodayRange(Connection connection, UUID id, UUID tenantId) throws Exception {
        // Acesso direto à partição exigiria grant nela; via tabela-pai, a faixa
        // de created_at só pode estar na partição do dia (não há partição default:
        // sem ela, o INSERT teria falhado). GUC do tenant, pela RLS.
        OutboxItDatabase.setTenantSession(connection, tenantId);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM public.outbox WHERE id = ?"
                        + " AND created_at >= (((NOW() AT TIME ZONE 'UTC')::DATE)::TIMESTAMP AT TIME ZONE 'UTC')"
                        + " AND created_at < ((((NOW() AT TIME ZONE 'UTC')::DATE) + 1)::TIMESTAMP AT TIME ZONE 'UTC')")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean partitionExists(Connection connection, String partition) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM pg_inherits WHERE inhparent = 'public.outbox'::regclass"
                        + " AND inhrelid = ?::regclass")) {
            ps.setString(1, "public." + partition);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    @Test
    @DisplayName("publicar a mesma mensagem duas vezes na mesma transacao grava uma linha")
    void duplicateInSameTransactionWritesOneRow() throws Exception {
        var msg = message(UUID.randomUUID());
        try (Connection connection = OutboxItDatabase.appConnection()) {
            connection.setAutoCommit(false);
            var queue = queueOn(connection);
            queue.publish(msg);
            queue.publish(msg);
            connection.commit();
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            OutboxItDatabase.setTenantSession(connection, msg.tenantId());
            assertThat(OutboxItDatabase.countById(connection, msg.messageId())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("publicar a mesma mensagem em duas transacoes grava uma linha")
    void duplicateAcrossTransactionsWritesOneRow() throws Exception {
        var msg = message(UUID.randomUUID());
        try (Connection connection = OutboxItDatabase.appConnection()) {
            queueOn(connection).publish(msg);
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            queueOn(connection).publish(msg);
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            OutboxItDatabase.setTenantSession(connection, msg.tenantId());
            assertThat(OutboxItDatabase.countById(connection, msg.messageId())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("transacao do negocio desfeita nao deixa mensagem")
    void rollbackLeavesNoMessage() throws Exception {
        var msg = message(UUID.randomUUID());
        try (Connection connection = OutboxItDatabase.appConnection()) {
            connection.setAutoCommit(false);
            queueOn(connection).publish(msg);
            connection.rollback();
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            OutboxItDatabase.setTenantSession(connection, msg.tenantId());
            assertThat(OutboxItDatabase.countById(connection, msg.messageId())).isZero();
        }
    }

    @Test
    @DisplayName("carga fora de JSON e recusada e nao deixa linha")
    void invalidJsonPayloadRefused() throws Exception {
        var msg = new MessageQueue.QueuedMessage(UUID.randomUUID(), UUID.randomUUID(), TYPE, "nao-json");
        try (Connection connection = OutboxItDatabase.appConnection()) {
            assertThatThrownBy(() -> queueOn(connection).publish(msg))
                    .isInstanceOf(IllegalStateException.class);
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            OutboxItDatabase.setTenantSession(connection, msg.tenantId());
            assertThat(OutboxItDatabase.countById(connection, msg.messageId())).isZero();
        }
    }

    @Test
    @DisplayName("aplicacao com contexto do tenant A nao le mensagens do tenant B")
    void appRoleSeesOnlyOwnTenant() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var msgA = message(tenantA);
        var msgB = message(tenantB);
        try (Connection connection = OutboxItDatabase.appConnection()) {
            queueOn(connection).publish(msgA);
            queueOn(connection).publish(msgB);
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            OutboxItDatabase.setTenantSession(connection, tenantA);
            assertThat(OutboxItDatabase.countById(connection, msgA.messageId())).isEqualTo(1);
            assertThat(OutboxItDatabase.countById(connection, msgB.messageId())).isZero();
        }
        try (Connection connection = OutboxItDatabase.appConnection()) {
            OutboxItDatabase.setTenantSession(connection, tenantB);
            assertThat(OutboxItDatabase.countById(connection, msgB.messageId())).isEqualTo(1);
            assertThat(OutboxItDatabase.countById(connection, msgA.messageId())).isZero();
        }
    }

    @Test
    @DisplayName("worker le mensagens de todos os tenants, sem contexto")
    void workerRoleSeesAllTenants() throws Exception {
        var msgA = message(UUID.randomUUID());
        var msgB = message(UUID.randomUUID());
        try (Connection connection = OutboxItDatabase.appConnection()) {
            queueOn(connection).publish(msgA);
            queueOn(connection).publish(msgB);
        }
        try (Connection connection = OutboxItDatabase.workerConnection()) {
            assertThat(OutboxItDatabase.countById(connection, msgA.messageId())).isEqualTo(1);
            assertThat(OutboxItDatabase.countById(connection, msgB.messageId())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("mensagem cai na particao do dia (UTC) e a funcao cria o dia seguinte")
    void messageLandsInTodayPartition() throws Exception {
        var msg = message(UUID.randomUUID());
        String today = "outbox_" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        String tomorrow = "outbox_" + LocalDate.now(ZoneOffset.UTC).plusDays(1)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        try (Connection connection = OutboxItDatabase.appConnection()) {
            queueOn(connection).publish(msg);
            assertThat(countInTodayRange(connection, msg.messageId(), msg.tenantId())).isEqualTo(1);
            assertThat(partitionExists(connection, today)).isTrue();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT public.outbox_ensure_partition((NOW() AT TIME ZONE 'UTC')::DATE + 2)")) {
                ps.executeQuery().close();
            }
            String dayAfterTomorrow = "outbox_" + LocalDate.now(ZoneOffset.UTC).plusDays(2)
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
            assertThat(partitionExists(connection, dayAfterTomorrow)).isTrue();
            assertThat(partitionExists(connection, tomorrow)).isTrue();
        }
    }
}
