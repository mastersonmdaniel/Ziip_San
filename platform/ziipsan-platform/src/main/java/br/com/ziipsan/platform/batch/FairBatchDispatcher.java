package br.com.ziipsan.platform.batch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * P2.1: fila justa entre tenants (RNF-25).
 *
 * <p>Regras, sem exceção:
 * <ul>
 *   <li>um tenant ocupa no máximo 50% dos slots (piso de 1 slot): com 4
 *       slots, nenhum tenant passa de 2 execuções simultâneas, mesmo que
 *       seja o único com trabalho na fila — os demais slots ficam livres
 *       para o próximo tenant que chegar, em vez de serem tomados pelo
 *       lote grande;</li>
 *   <li>a escolha é round-robin entre os tenants com trabalho pendente:
 *       a espera de um tenant não cresce com o lote de outro — o tenant
 *       pequeno que chega depois do grande começa sem esperar o lote
 *       grande terminar;</li>
 *   <li>a ordem dentro de um tenant é FIFO (as tarefas de um tenant
 *       saem na ordem em que foram submetidas).</li>
 * </ul>
 *
 * <p>As tarefas aqui são execuções inteiras de lote (o
 * {@link BatchJobService} submete uma tarefa por execução; as rotas
 * dentro da execução correm em sequência, na ordem declarada). O
 * paralelismo entre rotas de tenants diferentes é o que a fila justa
 * regula; a precedência entre rotas do mesmo lote é da execução.
 */
public final class FairBatchDispatcher implements AutoCloseable {

    private final int slots;
    private final int perTenantCap;
    private final List<Thread> workers = new ArrayList<>();
    private final Map<UUID, ArrayDeque<QueuedTask<?>>> queues = new LinkedHashMap<>();
    private final Map<UUID, Integer> active = new HashMap<>();
    private final List<UUID> order = new ArrayList<>();
    private int roundRobin;
    private boolean closed;

    private record QueuedTask<T>(UUID tenantId, Callable<T> task, CompletableFuture<T> future) {
    }

    public FairBatchDispatcher(int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException("A fila precisa de pelo menos 1 slot");
        }
        this.slots = slots;
        this.perTenantCap = Math.max(1, slots / 2);
        for (int i = 0; i < slots; i++) {
            Thread worker = new Thread(this::workerLoop, "batch-dispatcher-" + i);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
    }

    /** Teto de execuções simultâneas por tenant (50% dos slots, piso 1). */
    public int perTenantCap() {
        return perTenantCap;
    }

    public int slots() {
        return slots;
    }

    public <T> Future<T> submit(UUID tenantId, Callable<T> task) {
        Objects.requireNonNull(tenantId, "O tenant é obrigatório");
        Objects.requireNonNull(task, "A tarefa é obrigatória");
        CompletableFuture<T> future = new CompletableFuture<>();
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("O dispatcher está fechado");
            }
            queues.computeIfAbsent(tenantId, k -> {
                order.add(tenantId);
                return new ArrayDeque<>();
            }).add(new QueuedTask<>(tenantId, task, future));
            notifyAll();
        }
        return future;
    }

    private void workerLoop() {
        while (true) {
            QueuedTask<?> next;
            synchronized (this) {
                next = null;
                while (!closed && next == null) {
                    next = pickNext();
                    if (next == null) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                if (closed || next == null) {
                    return;
                }
                active.merge(next.tenantId(), 1, Integer::sum);
            }
            runTask(next);
            synchronized (this) {
                active.merge(next.tenantId(), -1, Integer::sum);
                notifyAll();
            }
        }
    }

    /** Round-robin entre tenants com fila e abaixo do teto; null se ninguém pode correr. */
    private QueuedTask<?> pickNext() {
        if (order.isEmpty()) {
            return null;
        }
        for (int i = 0; i < order.size(); i++) {
            int idx = (roundRobin + i) % order.size();
            UUID tenantId = order.get(idx);
            ArrayDeque<QueuedTask<?>> queue = queues.get(tenantId);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            if (active.getOrDefault(tenantId, 0) >= perTenantCap) {
                continue;
            }
            roundRobin = (idx + 1) % order.size();
            return queue.poll();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> void runTask(QueuedTask<T> task) {
        try {
            task.future().complete(task.task().call());
        } catch (Throwable e) {
            task.future().completeExceptionally(e);
        }
    }

    @Override
    public void close() {
        List<Thread> toStop;
        synchronized (this) {
            closed = true;
            notifyAll();
            toStop = new ArrayList<>(workers);
        }
        for (Thread worker : toStop) {
            worker.interrupt();
        }
    }
}
