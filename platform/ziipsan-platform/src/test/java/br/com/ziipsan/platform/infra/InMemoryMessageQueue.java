package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.queue.MessageQueue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Duplo em memoria de {@link MessageQueue}, SO PARA TESTE (congelar o contrato antes
 * da P1.9). Nao e implementacao de producao: a de producao e a outbox no PostgreSQL
 * com o worker, da P1.9, que deve passar na mesma suite.
 */
public final class InMemoryMessageQueue implements MessageQueue {

    private final List<QueuedMessage> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(QueuedMessage message) {
        published.add(message);
    }

    public List<QueuedMessage> published() {
        return List.copyOf(published);
    }
}
