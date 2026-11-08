package br.com.ziipsan.core.stationops;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Execucao de UMA ronda sobre um {@link RoundRoute}: nasce agendada,
 * e executada (em campo, possivelmente SEM REDE) e concluida com o
 * resultado item a item do checklist. A conclusao exige todos os
 * itens obrigatorios respondidos — ronda "completa" sem checklist e
 * exatamente o falso positivo que a apuracao de cobertura nao pode
 * aceitar.
 *
 * <p>Offline: {@code clientExecutionKey} e a identidade que o
 * aparelho gera ao criar a execucao local; e ela que torna a
 * sincronizacao idempotente ({@link RoundSyncService}). Os instantes
 * sao os do aparelho ({@code deviceRecordedAt} preservado na
 * sincronizacao), nao os da chegada ao servidor.</p>
 */
public final class RoundExecution {

    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, MISSED }

    public enum ItemResult { OK, NOT_OK, NOT_APPLICABLE }

    public record ItemAnswer(String itemKey, ItemResult result, String note) {
        public ItemAnswer {
            if (itemKey == null || itemKey.isBlank())
                throw new IllegalArgumentException("itemKey da resposta e obrigatorio");
            if (result == null) throw new IllegalArgumentException("resultado do item " + itemKey + " e obrigatorio");
            if (result == ItemResult.NOT_OK && (note == null || note.isBlank()))
                throw new IllegalArgumentException("item " + itemKey + " marcado NOT_OK exige a observacao");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final RoundRoute route;
    private final Instant dueAt;
    private final String clientExecutionKey;
    private Status status = Status.SCHEDULED;
    private String executorRef;
    private Instant startedAt;
    private Instant completedAt;
    private final Map<String, ItemAnswer> answers = new LinkedHashMap<>();

    public RoundExecution(UUID id, UUID tenantId, RoundRoute route, Instant dueAt, String clientExecutionKey) {
        if (id == null) throw new IllegalArgumentException("id da execucao e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId da execucao e obrigatorio");
        if (route == null) throw new IllegalArgumentException("roteiro da execucao e obrigatorio");
        if (!route.tenantId().equals(tenantId))
            throw new IllegalArgumentException("roteiro de outro tenant");
        if (dueAt == null) throw new IllegalArgumentException("vencimento (dueAt) da ronda e obrigatorio");
        if (clientExecutionKey == null || clientExecutionKey.isBlank())
            throw new IllegalArgumentException("clientExecutionKey (identidade offline) e obrigatoria");
        this.id = id;
        this.tenantId = tenantId;
        this.route = route;
        this.dueAt = dueAt;
        this.clientExecutionKey = clientExecutionKey;
    }

    public void start(String executorRef, Instant at) {
        if (status != Status.SCHEDULED) throw new IllegalStateException(
                "ronda " + id + " em " + status + " nao pode iniciar");
        if (executorRef == null || executorRef.isBlank())
            throw new IllegalArgumentException("executor da ronda e obrigatorio");
        if (at == null) throw new IllegalArgumentException("instante de inicio e obrigatorio");
        this.executorRef = executorRef;
        this.startedAt = at;
        this.status = Status.IN_PROGRESS;
    }

    public void answer(ItemAnswer answer) {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException(
                "ronda " + id + " em " + status + " nao aceita resposta de checklist");
        if (answer == null) throw new IllegalArgumentException("resposta e obrigatoria");
        if (!route.hasItem(answer.itemKey()))
            throw new IllegalArgumentException("item " + answer.itemKey() + " nao existe no roteiro "
                    + route.code());
        answers.put(answer.itemKey(), answer);
    }

    /** Conclui: todos os itens obrigatorios do roteiro precisam estar respondidos. */
    public void complete(Instant at) {
        if (status != Status.IN_PROGRESS) throw new IllegalStateException(
                "ronda " + id + " em " + status + " nao pode concluir");
        if (at == null || startedAt == null || at.isBefore(startedAt))
            throw new IllegalArgumentException("instante de conclusao anterior ao inicio da ronda");
        for (RoundRoute.ChecklistItem item : route.items()) {
            if (item.required() && !answers.containsKey(item.itemKey()))
                throw new IllegalStateException("ronda " + id + " nao conclui: item obrigatorio "
                        + item.itemKey() + " sem resposta");
        }
        this.completedAt = at;
        this.status = Status.COMPLETED;
    }

    /** Marca como nao realizada (a apuracao chama; uma execucao concluida nunca vira perdida). */
    public void markMissed() {
        if (status == Status.COMPLETED) throw new IllegalStateException(
                "ronda " + id + " concluida nao pode ser marcada como nao realizada");
        this.status = Status.MISSED;
    }

    /** Vencida = o vencimento passou e a ronda nao foi concluida. */
    public boolean isOverdueAt(Instant now) {
        return status != Status.COMPLETED && now.isAfter(dueAt);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public RoundRoute route() { return route; }
    public UUID unitId() { return route.unitId(); }
    public Instant dueAt() { return dueAt; }
    public String clientExecutionKey() { return clientExecutionKey; }
    public Status status() { return status; }
    public String executorRef() { return executorRef; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Map<String, ItemAnswer> answers() { return Map.copyOf(answers); }
    public boolean isCompleted() { return status == Status.COMPLETED; }
}
