package br.com.ziipsan.platform.infra.queue;

import java.util.UUID;

/**
 * P0.45: fila de mensagens. So a interface e a suite de contrato ficam aqui.
 * A implementacao sobre a tabela {@code outbox} no PostgreSQL com o {@code worker}
 * e da P1.9 (ADR-0014: uma outbox por banco) e deve passar na mesma suite — NAO ha
 * implementacao de producao neste modulo, e o duplo em memoria usado nos testes
 * ({@code InMemoryMessageQueue}, em test) nao e implementacao de producao.
 *
 * <p>Sem garantia de entrega aqui: a semantica de entrega (ao menos uma vez, com
 * deduplicacao pelo {@code messageId}) e da implementacao da P1.9. O publicador so
 * garante que a mensagem foi aceita para publicacao.
 */
public interface MessageQueue {

    /** Mensagem a publicar. {@code messageId} e o id de deduplicacao da P1.9. */
    record QueuedMessage(UUID messageId, UUID tenantId, String type, String payloadJson) {
        public QueuedMessage {
            if (messageId == null || tenantId == null) {
                throw new IllegalArgumentException("messageId e tenantId sao obrigatorios");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("type da mensagem e obrigatorio");
            }
            if (payloadJson == null) {
                throw new IllegalArgumentException("payloadJson e obrigatorio");
            }
        }
    }

    void publish(QueuedMessage message);
}
