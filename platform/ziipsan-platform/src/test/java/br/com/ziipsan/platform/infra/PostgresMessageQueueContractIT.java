package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.9: a MESMA suíte de contrato da P0.45
 * ({@link MessageQueueContractTest}), agora contra a implementação real
 * sobre a tabela outbox — o aceite explícito da P1.9. O duplo em memória
 * continua existindo só para congelar o contrato antes da P1.9.
 */
class PostgresMessageQueueContractIT extends MessageQueueContractTest {

    private Connection connection;
    private SingleConnectionDataSource dataSource;

    @BeforeAll
    static void ensureDatabase() {
        OutboxItDatabase.ensure();
    }

    @BeforeEach
    void openConnection() throws Exception {
        connection = OutboxItDatabase.appConnection();
        dataSource = new SingleConnectionDataSource(connection, true);
    }

    @AfterEach
    void closeConnection() throws Exception {
        connection.close();
    }

    @Override
    protected MessageQueue queue() {
        return new PostgresMessageQueue(dataSource);
    }

    @Override
    protected void assertPublished(MessageQueue.QueuedMessage message) {
        try {
            // A role da aplicação só lê as linhas do tenant do contexto (RLS).
            OutboxItDatabase.setTenantSession(connection, message.tenantId());
            assertThat(OutboxItDatabase.countById(connection, message.messageId())).isEqualTo(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
