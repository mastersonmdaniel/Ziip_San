package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.quality.CorrectionQueueItem;
import br.com.ziipsan.commercial.registry.quality.CorrectionQueueService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Recadastramento (ED-16, 18d-E11, ADR-0039): a campanha de campo sobre
 * a fila de correcao da qualidade cadastral da P3.32.
 *
 * <h2>O fluxo, na ordem</h2>
 * <ol>
 *   <li><b>Lote = OS interna de lote.</b> {@link #openBatch} entrega os
 *       itens escolhidos a fila da P3.32, que os agrupa numa OS interna
 *       de lote (gateway desta entrega, tipo
 *       {@code RECADASTRAMENTO_BATCH}); o lote so existe com essa OS.
 *       Item nao agrupavel (cliente sem unidade ligada) volta NOMEADO
 *       no resultado — nunca some em silencio.</li>
 *   <li><b>Coleta no app de campo da ED-18.</b> {@link #ingest} recebe a
 *       sincronizacao campo a campo: portao do aparelho, idempotencia
 *       por {@code task_id}, campo dentro do catalogo pre-definido da
 *       P1.3/P1.31 e unidade dentro do lote. Cada recusa e registrada
 *       como {@link CollectionError} (relatorio SG-911) antes da
 *       excecao. A coleta NAO toca o cadastro: ela guarda o valor
 *       coletado e o valor vigente naquele instante.</li>
 *   <li><b>Aprovacao campo a campo.</b> {@link #approve} e
 *       {@link #reject} decidem UM campo por chamada — nao existe
 *       aprovacao em massa neste servico (ha um teste de arquitetura
 *       que o garante). Rejeicao exige motivo.</li>
 *   <li><b>Aplicacao como efeito da OS de lote, com historico.</b>
 *       {@link #applyApproved} aplica SOMENTE campos APPROVED, um a um,
 *       pela porta {@link CadastralChangeApplier}, com o
 *       {@code serviceOrderId} do lote, gravando o
 *       {@link RecadastramentoHistoryEntry} de cada alteracao com
 *       diferenca. Antes de aplicar, o valor vigente e relido: se o
 *       cadastro mudou depois da coleta, o campo vira {@code STALE}
 *       (ocorrencia de recadastramento incorreto, SG-912) e NAO e
 *       aplicado — sobrescrever apagaria a alteracao mais nova.</li>
 *   <li><b>Encerramento.</b> {@link #closeBatch} recusa fechar com campo
 *       sem decisao, aprovado nao aplicado ou STALE aguardando nova
 *       coleta. Ao fechar, executa a OS (o efeito confirma cada alvo
 *       uma vez, via {@link #onBatchEffectConfirmed}) e resolve os itens
 *       da fila PELA OS — o unico caminho de resolucao da P3.32.</li>
 * </ol>
 *
 * <p>Fora desta entrega, por desenho do plano: o cruzamento com o
 * cadastro imobiliario municipal (CAD-103) e da ED-95 — nenhum codigo
 * aqui importa, cruza ou sugere dado municipal.
 */
public final class RecadastramentoService {

    /** Resultado da abertura: o lote e os itens nao agrupaveis, nomeados. */
    public record OpenBatchResult(RecadastramentoBatch batch, List<UUID> ungroupableItemIds) {
    }

    /** Recibo de uma coleta aceita (ou do reenvio identico dela). */
    public record CollectionReceipt(UUID collectedFieldId, UUID taskId, boolean duplicate, boolean difference) {
    }

    /** Contagem de uma rodada de aplicacao do lote. */
    public record ApplyResult(int applied, int withoutDifference, int stale, int awaitingDecision, int rejected) {
    }

    private final RecadastramentoStore store;
    private final CorrectionQueueService queue;
    private final BatchOrderCloser closer;
    private final CadastralFieldReader reader;
    private final CadastralChangeApplier applier;
    private final CollectionDeviceGate deviceGate;

    public RecadastramentoService(RecadastramentoStore store, CorrectionQueueService queue,
                                  BatchOrderCloser closer, CadastralFieldReader reader,
                                  CadastralChangeApplier applier, CollectionDeviceGate deviceGate) {
        if (store == null || queue == null || closer == null || reader == null
                || applier == null || deviceGate == null) {
            throw new IllegalArgumentException(
                    "store, fila da P3.32, closer da OS, leitor, aplicador e portao do aparelho sao obrigatorios");
        }
        this.store = store;
        this.queue = queue;
        this.closer = closer;
        this.reader = reader;
        this.applier = applier;
        this.deviceGate = deviceGate;
    }

    /**
     * Abre o lote de recadastramento a partir de itens OPEN da fila da
     * P3.32: a fila abre a OS interna de lote e marca os itens BATCHED
     * nela; este servico guarda o lote apontando para a mesma OS.
     */
    public OpenBatchResult openBatch(List<UUID> queueItemIds, UUID actorId, OffsetDateTime now) {
        if (queueItemIds == null || queueItemIds.isEmpty()) {
            throw new IllegalArgumentException("nenhum item da fila informado para o lote");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("ator e obrigatorio para abrir o lote");
        }
        CorrectionQueueService.BatchResult result = queue.openCorrectionBatch(queueItemIds, actorId);
        if (result.opened().isEmpty()) {
            throw new IllegalStateException("nenhum item agrupavel para o lote: "
                    + result.ungroupable().size() + " item(ns) sem unidade ligada ou fora de OPEN");
        }
        CorrectionQueueService.OpenedBatch opened = result.opened().get(0);
        RecadastramentoBatch batch = RecadastramentoBatch.open(opened.serviceOrderId(),
                opened.itemIds(), opened.targetUnitIds(), actorId, now);
        store.saveBatch(batch);
        List<UUID> ungroupable = result.ungroupable().stream().map(CorrectionQueueItem::id).toList();
        return new OpenBatchResult(batch, ungroupable);
    }

    /**
     * Recebe UMA coleta de campo da sincronizacao da ED-18. Recusas de
     * negocio sao registradas como erro de coleta e lancadas como
     * {@link IllegalArgumentException}; o portao do aparelho lanca a
     * excecao dele ANTES de qualquer registro (aparelho recusado nao
     * sincroniza, nem para gerar erro).
     */
    public CollectionReceipt ingest(UUID tenantId, FieldCollectionSubmission submission, OffsetDateTime now) {
        if (tenantId == null || submission == null) {
            throw new IllegalArgumentException("tenant e submissao sao obrigatorios para a coleta");
        }
        if (submission.taskId() == null || submission.batchId() == null
                || submission.deviceId() == null || submission.collectorId() == null) {
            throw new IllegalArgumentException(
                    "coleta sem tarefa, lote, aparelho ou recadastrador nao e registravel");
        }
        deviceGate.assertCanSync(tenantId, submission.deviceId(), now);
        RecadastramentoBatch batch = store.findBatch(submission.batchId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "lote de recadastramento nao encontrado: " + submission.batchId()));

        var prior = store.findCollectedByTask(batch.id(), submission.taskId());
        if (prior.isPresent()) {
            CollectedField existing = prior.get();
            String fingerprint = CollectedField.fingerprintOf(batch.id(), submission.unitId(),
                    submission.customerId(), submission.targetType(), submission.field(),
                    submission.collectedValue());
            if (existing.payloadFingerprint().equals(fingerprint)) {
                return new CollectionReceipt(existing.id(), existing.taskId(), true, existing.hasDifference());
            }
            throw fail(batch, submission, CollectionErrorCode.TASK_PAYLOAD_CONFLICT,
                    "task_id reenviado com carga diferente da coleta gravada", now);
        }
        if (batch.status() != RecadastramentoBatch.Status.OPEN) {
            throw fail(batch, submission, CollectionErrorCode.BATCH_NOT_OPEN,
                    "lote " + batch.id() + " nao esta aberto para coleta", now);
        }
        if (!batch.isTargetUnit(submission.unitId())) {
            throw fail(batch, submission, CollectionErrorCode.UNIT_NOT_IN_BATCH,
                    "unidade " + submission.unitId() + " nao e alvo do lote", now);
        }
        if (!RecadastramentoFieldCatalog.isCollectable(submission.targetType(), submission.field())) {
            throw fail(batch, submission, CollectionErrorCode.FIELD_NOT_PREDEFINED,
                    "campo fora do mapa pre-definido: " + submission.targetType() + "." + submission.field(), now);
        }
        if (RecadastramentoFieldCatalog.TARGET_CUSTOMER.equals(submission.targetType())
                && submission.customerId() == null) {
            throw fail(batch, submission, CollectionErrorCode.TARGET_MISMATCH,
                    "campo de cliente coletado sem o cliente: " + submission.field(), now);
        }
        if (submission.collectedValue() == null || submission.collectedValue().isBlank()) {
            throw fail(batch, submission, CollectionErrorCode.VALUE_BLANK,
                    "valor coletado vazio para o campo " + submission.field(), now);
        }
        if (RecadastramentoFieldCatalog.requiresMaskedValue(submission.targetType(), submission.field())
                && !submission.valueMasked()) {
            throw fail(batch, submission, CollectionErrorCode.DOCUMENT_NOT_MASKED,
                    "documento coletado sem mascara (regra da P1.3)", now);
        }

        UUID targetId = RecadastramentoFieldCatalog.TARGET_CUSTOMER.equals(submission.targetType())
                ? submission.customerId() : submission.unitId();
        String current = reader.currentValue(submission.targetType(), targetId, submission.field());

        // Nova coleta de um campo STALE do mesmo lote supersede a antiga:
        // a antiga fica como ocorrencia (SG-912), a nova segue o fluxo.
        for (CollectedField other : store.findCollectedByBatch(batch.id())) {
            if (other.status() == CollectedField.Status.STALE
                    && other.targetType().equals(submission.targetType())
                    && other.field().equals(submission.field())
                    && Objects.equals(other.targetId(), targetId)) {
                other.markSuperseded();
                store.saveCollected(other);
            }
        }

        CollectedField collected = CollectedField.collect(batch.id(), submission.taskId(),
                submission.unitId(), submission.customerId(), submission.targetType(), submission.field(),
                current, submission.collectedValue(), submission.collectorId(), submission.photoRefs(),
                submission.capturedAt() == null ? now : submission.capturedAt());
        store.saveCollected(collected);
        return new CollectionReceipt(collected.id(), collected.taskId(), false, collected.hasDifference());
    }

    /** Aprova UM campo coletado (a aprovacao e campo a campo). */
    public CollectedField approve(UUID collectedId, UUID approverId, OffsetDateTime now) {
        CollectedField collected = requireCollected(collectedId);
        collected.approve(approverId, now);
        store.saveCollected(collected);
        return collected;
    }

    /** Rejeita UM campo coletado, com motivo obrigatorio. */
    public CollectedField reject(UUID collectedId, UUID approverId, String reason, OffsetDateTime now) {
        CollectedField collected = requireCollected(collectedId);
        collected.reject(approverId, reason, now);
        store.saveCollected(collected);
        return collected;
    }

    /**
     * Aplica os campos APPROVED do lote como efeito da OS de lote, campo
     * a campo, com historico. Rodar de novo nao reaplica: campo APPLIED
     * nao volta a APPROVED, e a porta de aplicacao so e chamada para
     * APPROVED (idempotencia da aplicacao, alem da do motor de OS).
     */
    public ApplyResult applyApproved(UUID batchId, UUID actorId, OffsetDateTime now) {
        if (actorId == null) {
            throw new IllegalArgumentException("ator e obrigatorio para aplicar o lote");
        }
        RecadastramentoBatch batch = requireBatch(batchId);
        int applied = 0;
        int withoutDifference = 0;
        int stale = 0;
        int awaitingDecision = 0;
        int rejected = 0;
        for (CollectedField collected : store.findCollectedByBatch(batch.id())) {
            switch (collected.status()) {
                case COLLECTED -> awaitingDecision++;
                case REJECTED -> rejected++;
                case APPROVED -> {
                    String current = reader.currentValue(collected.targetType(),
                            collected.targetId(), collected.field());
                    if (!Objects.equals(normalize(current), normalize(collected.valueAtCollection()))) {
                        collected.markStale(current);
                        store.saveCollected(collected);
                        stale++;
                    } else if (!collected.hasDifference()) {
                        collected.markApplied(null, now);
                        store.saveCollected(collected);
                        withoutDifference++;
                    } else {
                        UUID changeRequestId = applier.apply(batch.serviceOrderId(),
                                collected.targetType(), collected.targetId(), collected.field(),
                                collected.valueAtCollection(), collected.collectedValue(), actorId);
                        collected.markApplied(changeRequestId, now);
                        store.saveCollected(collected);
                        store.saveHistory(RecadastramentoHistoryEntry.of(batch, collected, changeRequestId, now));
                        applied++;
                    }
                }
                case APPLIED, STALE, SUPERSEDED -> {
                    // Terminais (ou aguardando nova coleta, no caso STALE):
                    // nada a aplicar nesta rodada.
                }
            }
        }
        return new ApplyResult(applied, withoutDifference, stale, awaitingDecision, rejected);
    }

    /**
     * Manipulador da confirmacao do efeito da OS de lote (ligado na
     * fiacao ao gateway): registra, por alvo, que o motor aplicou o
     * efeito da OS. NAO resolve a fila — a resolucao acontece uma unica
     * vez, no fim do {@link #closeBatch}, pela OS inteira.
     */
    public void onBatchEffectConfirmed(UUID serviceOrderId, UUID unitId, List<String> findingKeys) {
        RecadastramentoBatch batch = store.findBatchByServiceOrderId(serviceOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "confirmacao de efeito para OS sem lote de recadastramento: " + serviceOrderId));
        batch.recordEffectConfirmation(unitId);
        store.saveBatch(batch);
    }

    /**
     * Encerra o lote: exige todo campo coletado em estado terminal
     * (APPLIED, REJECTED ou SUPERSEDED), executa a OS de lote (efeito
     * confirmado por alvo) e resolve os itens da fila PELA OS.
     */
    public RecadastramentoBatch closeBatch(UUID batchId, UUID actorId, OffsetDateTime now) {
        if (actorId == null) {
            throw new IllegalArgumentException("ator e obrigatorio para encerrar o lote");
        }
        RecadastramentoBatch batch = requireBatch(batchId);
        if (batch.status() != RecadastramentoBatch.Status.OPEN) {
            throw new IllegalStateException("lote " + batch.id() + " nao esta OPEN para encerrar");
        }
        long pending = store.findCollectedByBatch(batch.id()).stream()
                .filter(c -> c.status() == CollectedField.Status.COLLECTED
                        || c.status() == CollectedField.Status.APPROVED
                        || c.status() == CollectedField.Status.STALE)
                .count();
        if (pending > 0) {
            throw new IllegalStateException("encerramento recusado: " + pending
                    + " campo(s) sem decisao, aprovados nao aplicados ou STALE aguardando nova coleta no lote "
                    + batch.id());
        }
        closer.executeAndClose(batch.serviceOrderId(), actorId);
        queue.resolveByServiceOrder(batch.serviceOrderId(), actorId);
        batch.close(actorId, now);
        store.saveBatch(batch);
        return batch;
    }

    private CollectedField requireCollected(UUID collectedId) {
        if (collectedId == null) {
            throw new IllegalArgumentException("campo coletado e obrigatorio");
        }
        return store.findCollected(collectedId)
                .orElseThrow(() -> new IllegalArgumentException("campo coletado nao encontrado: " + collectedId));
    }

    private RecadastramentoBatch requireBatch(UUID batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("lote e obrigatorio");
        }
        return store.findBatch(batchId)
                .orElseThrow(() -> new IllegalArgumentException("lote de recadastramento nao encontrado: " + batchId));
    }

    /** Registra o erro de coleta e devolve a excecao a lancar. */
    private IllegalArgumentException fail(RecadastramentoBatch batch, FieldCollectionSubmission submission,
                                          CollectionErrorCode code, String detail, OffsetDateTime now) {
        store.saveError(CollectionError.of(batch.id(), submission.taskId(), submission.unitId(),
                submission.targetType(), submission.field(), code, detail, submission.collectorId(), now));
        return new IllegalArgumentException(detail + " [" + code + "]");
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
