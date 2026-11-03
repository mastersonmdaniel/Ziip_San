package br.com.ziipsan.platform.infra.events;

import br.com.ziipsan.platform.infra.queue.MessageQueue;

import java.util.Objects;
import java.util.UUID;

/**
 * Caminho de publicação de eventos de negócio (P1.38). Toda tarefa que publica
 * um evento usa esta classe — nunca a fila crua: antes de qualquer escrita, o
 * evento tem de estar no catálogo, na versão declarada, com a carga dentro do
 * esquema. Fora do catálogo, em versão não declarada ou com carga fora do
 * esquema, a publicação é recusada com {@link IllegalArgumentException} e
 * nada é gravado na outbox.
 *
 * <p>O tipo gravado na outbox é versionado ({@code nome@vN}), para o
 * consumidor saber exatamente qual esquema a carga segue; a versão não é um
 * detalhe do nome do evento, e sim parte do contrato.
 */
public final class BusinessEventPublisher {

    private final MessageQueue queue;
    private final BusinessEventCatalog catalog;

    public BusinessEventPublisher(MessageQueue queue, BusinessEventCatalog catalog) {
        this.queue = Objects.requireNonNull(queue, "queue e obrigatoria");
        this.catalog = Objects.requireNonNull(catalog, "catalogo e obrigatorio");
    }

    /** Publica com identificador gerado; devolve o identificador da mensagem. */
    public UUID publish(UUID tenantId, String name, int version, String payloadJson) {
        UUID messageId = UUID.randomUUID();
        publish(messageId, tenantId, name, version, payloadJson);
        return messageId;
    }

    /** Publica com identificador explícito (deduplicação da outbox, P1.9). */
    public void publish(UUID messageId, UUID tenantId, String name, int version, String payloadJson) {
        Objects.requireNonNull(messageId, "messageId e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        catalog.requirePublishable(name, version, payloadJson);
        queue.publish(new MessageQueue.QueuedMessage(
                messageId, tenantId, name + "@v" + version, payloadJson));
    }
}
