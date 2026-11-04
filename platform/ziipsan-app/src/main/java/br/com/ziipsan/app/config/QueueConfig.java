package br.com.ziipsan.app.config;

import br.com.ziipsan.commercial.registry.importer.ImportJobQueue;
import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * P1.12: o publicador da outbox (P1.9) como bean, para o serviço de exportação
 * enfileirar a geração assíncrona. A publicação impõe o tenant da mensagem
 * como contexto da inserção (PostgresMessageQueue), então o DataSource da
 * aplicação serve aqui; o consumo pelo worker usa o DataSource da role
 * {@code ziipsan_worker} (ver OutboxWorker), que não é este bean.
 *
 * <p>P1-C (integracao): o adaptador do importador (P1.40) sobre a mesma fila.
 * O ImportService depende só da porta ImportJobQueue (módulo registry); este
 * adaptador é a única implementação e vive aqui, na raiz de composição, para
 * a regra P1.13 (commercial não depende de platform) continuar valendo.
 */
@Configuration
public class QueueConfig {

    @Bean
    public MessageQueue messageQueue(DataSource dataSource) {
        return new PostgresMessageQueue(dataSource);
    }

    @Bean
    public ImportJobQueue importJobQueue(MessageQueue messageQueue) {
        return (UUID jobId, UUID tenantId, String type, String payloadJson) ->
                messageQueue.publish(new MessageQueue.QueuedMessage(jobId, tenantId, type, payloadJson));
    }
}
