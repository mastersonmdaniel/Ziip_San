package br.com.ziipsan.commercial.registry.recadastramento;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lote de recadastramento (ED-16, ADR-0039): a campanha de campo sobre
 * um conjunto de itens da fila de correcao da P3.32.
 *
 * <p>O lote NASCE como OS interna de lote: sem {@code serviceOrderId}
 * nao ha lote (a fabrica recusa). Os itens da fila entram agrupados
 * nessa OS pela fila da P3.32 ({@code CorrectionQueueService}, com o
 * gateway desta entrega), e as unidades alvo sao as dos itens
 * agrupados. A coleta so acontece com o lote {@code OPEN}; o
 * encerramento executa a OS, cujo efeito confirma cada alvo uma unica
 * vez (garantia do motor da P1.31) — as confirmacoes ficam registradas
 * em {@code confirmedUnitIds} — e so entao a fila resolve os itens.
 */
public final class RecadastramentoBatch {

    public enum Status { OPEN, CLOSED }

    private final UUID id;
    private final UUID serviceOrderId;
    private final List<UUID> queueItemIds;
    private final List<UUID> targetUnitIds;
    private final UUID createdBy;
    private final OffsetDateTime createdAt;
    private final Set<UUID> confirmedUnitIds = new LinkedHashSet<>();
    private Status status;
    private UUID closedBy;
    private OffsetDateTime closedAt;

    private RecadastramentoBatch(UUID id, UUID serviceOrderId, List<UUID> queueItemIds,
                                 List<UUID> targetUnitIds, UUID createdBy, OffsetDateTime createdAt) {
        this.id = id;
        this.serviceOrderId = serviceOrderId;
        this.queueItemIds = queueItemIds == null ? List.of() : List.copyOf(queueItemIds);
        this.targetUnitIds = targetUnitIds == null ? List.of() : List.copyOf(targetUnitIds);
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.status = Status.OPEN;
    }

    /** Abre o lote sobre a OS interna de lote ja criada pela fila/gateway. */
    public static RecadastramentoBatch open(UUID serviceOrderId, List<UUID> queueItemIds,
                                            List<UUID> targetUnitIds, UUID createdBy,
                                            OffsetDateTime createdAt) {
        if (serviceOrderId == null) {
            throw new IllegalArgumentException(
                    "lote de recadastramento so existe como OS interna de lote (ADR-0039): OS obrigatoria");
        }
        if (queueItemIds == null || queueItemIds.isEmpty()) {
            throw new IllegalArgumentException("lote sem item da fila da P3.32 nao existe");
        }
        if (targetUnitIds == null || targetUnitIds.isEmpty()) {
            throw new IllegalArgumentException("lote sem unidade alvo nao existe");
        }
        if (createdBy == null) {
            throw new IllegalArgumentException("ator e obrigatorio para abrir o lote");
        }
        return new RecadastramentoBatch(UUID.randomUUID(), serviceOrderId, queueItemIds,
                targetUnitIds, createdBy, createdAt);
    }

    /** Reconstroi o lote persistido (fiacao JPA); o estado vem da linha. */
    public static RecadastramentoBatch restore(UUID id, UUID serviceOrderId, List<UUID> queueItemIds,
                                               List<UUID> targetUnitIds, UUID createdBy,
                                               OffsetDateTime createdAt, Status status,
                                               List<UUID> confirmedUnitIds, UUID closedBy,
                                               OffsetDateTime closedAt) {
        if (id == null || serviceOrderId == null || status == null) {
            throw new IllegalArgumentException("lote incompleto para restaurar");
        }
        RecadastramentoBatch batch = new RecadastramentoBatch(id, serviceOrderId, queueItemIds,
                targetUnitIds, createdBy, createdAt);
        batch.status = status;
        if (confirmedUnitIds != null) {
            batch.confirmedUnitIds.addAll(confirmedUnitIds);
        }
        batch.closedBy = closedBy;
        batch.closedAt = closedAt;
        return batch;
    }

    public boolean isTargetUnit(UUID unitId) {
        return unitId != null && targetUnitIds.contains(unitId);
    }

    /** Confirmacao do efeito da OS para um alvo (o motor garante uma vez por alvo). */
    public void recordEffectConfirmation(UUID unitId) {
        if (!isTargetUnit(unitId)) {
            throw new IllegalArgumentException("efeito confirmado para unidade fora do lote: " + unitId);
        }
        confirmedUnitIds.add(unitId);
    }

    public void close(UUID actorId, OffsetDateTime at) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("lote ja esta " + status + " e nao fecha de novo");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("ator e obrigatorio para encerrar o lote");
        }
        this.status = Status.CLOSED;
        this.closedBy = actorId;
        this.closedAt = at;
    }

    public UUID id() { return id; }
    public UUID serviceOrderId() { return serviceOrderId; }
    public List<UUID> queueItemIds() { return queueItemIds; }
    public List<UUID> targetUnitIds() { return targetUnitIds; }
    public UUID createdBy() { return createdBy; }
    public OffsetDateTime createdAt() { return createdAt; }
    public Set<UUID> confirmedUnitIds() { return Set.copyOf(confirmedUnitIds); }
    public Status status() { return status; }
    public UUID closedBy() { return closedBy; }
    public OffsetDateTime closedAt() { return closedAt; }
}
