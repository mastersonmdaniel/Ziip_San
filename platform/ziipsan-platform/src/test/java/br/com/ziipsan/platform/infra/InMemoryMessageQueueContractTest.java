package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.queue.MessageQueue;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.45: contrato de MessageQueue contra o duplo em memoria (a outbox da P1.9 herda a suite). */
class InMemoryMessageQueueContractTest extends MessageQueueContractTest {

    private final InMemoryMessageQueue queue = new InMemoryMessageQueue();

    @Override
    protected MessageQueue queue() {
        return queue;
    }

    @Override
    protected void assertPublished(MessageQueue.QueuedMessage message) {
        assertThat(queue.published()).contains(message);
    }
}
