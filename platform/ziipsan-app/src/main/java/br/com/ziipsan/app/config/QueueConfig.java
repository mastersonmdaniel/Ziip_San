package br.com.ziipsan.app.config;

import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.queue.PostgresMessageQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * P1.12: o publicador da outbox (P1.9) como bean, para o serviço de exportação
 * enfileirar a geração assíncrona. A publicação impõe o tenant da mensagem
 * como contexto da inserção (PostgresMessageQueue), então o DataSource da
 * aplicação serve aqui; o consumo pelo worker usa o DataSource da role
 * {@code ziipsan_worker} (ver OutboxWorker), que não é este bean.
 */
@Configuration
public class QueueConfig {

    @Bean
    public MessageQueue messageQueue(DataSource dataSource) {
        return new PostgresMessageQueue(dataSource);
    }
}
