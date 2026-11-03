package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.queue.MessageQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.45: suite de contrato de {@link MessageQueue}. A implementacao sobre a tabela
 * {@code outbox} (P1.9) deve estender esta suite com a implementacao real; aqui ela
 * roda contra o duplo em memoria (so teste), para congelar o contrato antes da P1.9.
 */
public abstract class MessageQueueContractTest {

    protected abstract MessageQueue queue();

    protected abstract void assertPublished(MessageQueue.QueuedMessage message);

    @Test
    @DisplayName("publicacao valida e aceita")
    void validPublishAccepted() {
        var message = new MessageQueue.QueuedMessage(UUID.randomUUID(), UUID.randomUUID(),
                "billing.invoice.issued", "{\"id\":1}");
        queue().publish(message);
        assertPublished(message);
    }

    @Test
    @DisplayName("mensagem sem ids ou sem tipo e recusada na construcao")
    void invalidMessageRefused() {
        assertThatThrownBy(() -> new MessageQueue.QueuedMessage(null, UUID.randomUUID(), "t", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessageQueue.QueuedMessage(UUID.randomUUID(), UUID.randomUUID(), " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
