package br.com.ziipsan.platform.infra.queue;

import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * P1.9: {@link MessageQueue} sobre a tabela {@code public.outbox} (core V8, ADR-0014).
 *
 * <p>Contrato da P0.45, implementação real: {@code messageId} vira {@code id} e
 * {@code dedup_key}; publicar duas vezes a mesma mensagem grava uma só linha.
 *
 * <p>Transação: o INSERT usa a conexão da transação do chamador (via
 * {@code DataSourceUtils}, o mesmo mecanismo do Spring). Dentro de um
 * {@code @Transactional}, a mensagem é gravada na mesma transação do negócio:
 * se o negócio desfaz, a mensagem some junto. Fora de transação, o publish
 * abre a sua própria transação local (atômica para o publish, mas sem vínculo
 * com nenhuma escrita de negócio — o chamador que precisa do vínculo tem de
 * chamar dentro de transação). Se a conexão já estiver com autoCommit
 * desligado por outro dono (transação manual), o publish participa dela e
 * NÃO dá commit: o dono da transação decide.
 *
 * <p>Tenant: a mensagem carrega o tenant explícito e o publish o impõe como
 * contexto transacional da inserção (salva o GUC anterior, impõe o da
 * mensagem, insere, restaura). Sem isso, a RLS da outbox recusaria a
 * inserção sempre que o contexto ambiente não fosse o da mensagem — o que
 * inclui o caso legítimo do provisionamento, que publica PARA um tenant sem
 * estar EM um tenant. A restauração imediata impede que o contexto vaze
 * para o resto da transação do chamador. Leitura continua isolada pela RLS:
 * a role da aplicação só lê as linhas do tenant do contexto.
 *
 * <p>Deduplicação: trava consultiva transacional sobre
 * (tenant, queue, dedup_key) + checagem de existência antes do INSERT, na
 * mesma transação. Publicadores concorrentes da mesma mensagem se
 * serializam na trava; o segundo vê a linha e não insere. O índice único
 * (tenant, queue, dedup_key, created_at) reforça dentro do dia.
 *
 * <p>Partição: antes do INSERT, o publish garante a partição do dia (UTC)
 * via {@code outbox_ensure_partition}, porque a role da aplicação não tem
 * CREATE. Carga sem JSON válido é recusada pelo cast para jsonb.
 */
public final class PostgresMessageQueue implements MessageQueue {

    /** Fila padrão: o contrato da P0.45 não tem campo de fila. */
    public static final String DEFAULT_QUEUE = "default";

    private final DataSource dataSource;
    private final String queue;

    public PostgresMessageQueue(DataSource dataSource) {
        this(dataSource, DEFAULT_QUEUE);
    }

    public PostgresMessageQueue(DataSource dataSource, String queue) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource obrigatorio");
        }
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("queue e obrigatoria");
        }
        this.dataSource = dataSource;
        this.queue = queue;
    }

    @Override
    public void publish(QueuedMessage message) {
        Objects.requireNonNull(message, "message e obrigatoria");
        Connection connection;
        try {
            connection = DataSourceUtils.getConnection(dataSource);
        } catch (RuntimeException e) {
            // Spring 7: getConnection envolve a SQLException em CannotGetJdbcConnectionException (unchecked).
            throw new IllegalStateException("falha ao obter conexao para a outbox", e);
        }
        boolean ownTransaction = false;
        try {
            boolean joined = DataSourceUtils.isConnectionTransactional(connection, dataSource)
                    || !connection.getAutoCommit();
            if (!joined) {
                connection.setAutoCommit(false);
                ownTransaction = true;
            }
            String savedTenant = currentTenant(connection);
            try {
                setTenant(connection, message.tenantId().toString());
                ensurePartition(connection);
                lockDedupKey(connection, message);
                if (!alreadyPublished(connection, message)) {
                    insert(connection, message);
                }
            } finally {
                restoreTenant(connection, savedTenant);
            }
            if (ownTransaction) {
                connection.commit();
            }
        } catch (SQLException e) {
            if (ownTransaction) {
                rollbackQuietly(connection);
            }
            throw new IllegalStateException("falha ao publicar na outbox", e);
        } finally {
            if (ownTransaction) {
                restoreAutoCommitQuietly(connection);
            }
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private String currentTenant(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT current_setting('app.current_tenant', true)");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void setTenant(Connection connection, String tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, true)")) {
            ps.setString(1, tenantId);
            ps.executeQuery().close();
        }
    }

    private void restoreTenant(Connection connection, String savedTenant) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, true)")) {
            ps.setString(1, savedTenant == null ? "" : savedTenant);
            ps.executeQuery().close();
        } catch (SQLException ignored) {
            // Transação abortada por erro anterior: não há o que restaurar.
        }
    }

    private void ensurePartition(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT public.outbox_ensure_partition((NOW() AT TIME ZONE 'UTC')::DATE)")) {
            ps.executeQuery().close();
        }
    }

    private void lockDedupKey(Connection connection, QueuedMessage message) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            ps.setString(1, message.tenantId() + "|" + queue + "|" + message.messageId());
            ps.executeQuery().close();
        }
    }

    private boolean alreadyPublished(Connection connection, QueuedMessage message) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM public.outbox WHERE tenant_id = ? AND queue = ? AND dedup_key = ? LIMIT 1")) {
            ps.setObject(1, message.tenantId());
            ps.setString(2, queue);
            ps.setString(3, message.messageId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insert(Connection connection, QueuedMessage message) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO public.outbox"
                        + " (id, tenant_id, queue, type, dedup_key, payload, status, available_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?::jsonb, 'PENDING', NOW())")) {
            ps.setObject(1, message.messageId());
            ps.setObject(2, message.tenantId());
            ps.setString(3, queue);
            ps.setString(4, message.type());
            ps.setString(5, message.messageId().toString());
            ps.setString(6, message.payloadJson());
            ps.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // O erro original já está a caminho do chamador.
        }
    }

    private void restoreAutoCommitQuietly(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // O pool repõe o estado ao devolver a conexão.
        }
    }
}
