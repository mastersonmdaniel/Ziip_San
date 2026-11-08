package br.com.ziipsan.commercial.billingrules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila das contas impressas e ainda nao enviadas (ED-19): "contas retidas
 * reenviadas e reimpressas". A fila e por {@code task_id}: reter a mesma
 * tarefa duas vezes (reimpressao, retentativa) NAO duplica a entrada — a
 * conta de campo e unica por task_id tambem na fila. Reimprimir nao muda o
 * resultado guardado: a reimpressao usa o {@link FieldBillResult} retido,
 * nunca recalcula por cima (a conta impressa e imutavel; correcao e nova
 * versao no servidor, fluxo da P2.20).
 *
 * <p>Classe de estado em memoria: no aparelho ela e alimentada a partir do
 * armazenamento local cifrado da ED-18 e, no servidor, pelos testes do
 * servico de submissao; a persistencia e de quem a usa.
 */
public final class RetainedBills {

    private final Map<UUID, FieldBillResult> byTask = new LinkedHashMap<>();

    /** Retem a conta impressa. Devolve {@code true} se era nova na fila. */
    public synchronized boolean retain(FieldBillResult printed) {
        Objects.requireNonNull(printed, "conta impressa a reter e obrigatoria");
        if (byTask.containsKey(printed.taskId())) {
            FieldBillResult existing = byTask.get(printed.taskId());
            if (!existing.fingerprint().equals(printed.fingerprint())) {
                throw new IllegalStateException("Ja existe conta retida para a task " + printed.taskId()
                        + " com outro fingerprint — a conta impressa nao muda; divergencia e do servidor");
            }
            return false;
        }
        byTask.put(printed.taskId(), printed);
        return true;
    }

    /** Contas retidas, na ordem em que foram impressas (ordem de reenvio/reimpressao). */
    public synchronized List<FieldBillResult> pending() {
        return List.copyOf(new ArrayList<>(byTask.values()));
    }

    public synchronized int size() {
        return byTask.size();
    }

    public synchronized boolean contains(UUID taskId) {
        return byTask.containsKey(taskId);
    }

    /** Baixa apos o envio confirmado pelo servidor. Tarefa fora da fila e erro. */
    public synchronized void markSent(UUID taskId) {
        if (byTask.remove(taskId) == null) {
            throw new IllegalArgumentException("Tarefa nao esta retida: " + taskId);
        }
    }
}
