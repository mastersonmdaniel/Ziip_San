package br.com.ziipsan.core.inventory;

import br.com.ziipsan.core.fieldos.MaterialUse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Baixa do material de campo na sincronizacao da ED-20 (ED-58 fecha o
 * laco aberto pela ED-20/ADR-0032): a ED-20 registra o usado POR TAREFA
 * com {@code task_id} e entrega as linhas; ESTE servico baixa.
 *
 * <ul>
 *   <li>IDEMPOTENTE por {@code task_id}: reenvio devolve o resultado
 *       marcado como duplicado e NAO baixa duas vezes;</li>
 *   <li>baixa AUTOMATICA com piso zero: sem fisico, baixa o que ha e a
 *       falta vira pendencia de regularizacao + alerta (nunca fisico
 *       negativo, nunca baixa silenciosa a maior);</li>
 *   <li>item CONTROLADO consumido em campo gera ALCADA A POSTERIORI:
 *       a baixa acontece (o material ja foi consumido fisicamente), mas
 *       nasce uma aprovacao posterior pendente; a REJEICAO dela nao
 *       desfaz a baixa — gera pendencia de regularizacao e auditoria
 *       (decisao de execucao declarada no relatorio);</li>
 *   <li>consumo por tarefa (incl. EPI) e registrado como evidencia para
 *       a ISO 45001 (ED-71).</li>
 * </ul>
 * A assinatura de {@link #writeOffDetailed} espelha a porta
 * {@code FieldOsPorts.StockWriteOffPort} da ED-20 (tenant, task_id,
 * linhas de MaterialUse); o local de estoque da equipe e informado na
 * fiacao (a porta nao carrega local) como parametro explicito.
 */
public final class FieldWriteOffService {

    public record WriteOffLineResult(String itemCode, BigDecimal requestedQty, BigDecimal writtenQty,
                                     BigDecimal shortfallQty, boolean controlled, boolean posteriorApprovalCreated) {}
    public record WriteOffResult(String taskId, boolean duplicate, List<WriteOffLineResult> lines) {}

    private final InventoryStore store;
    private final WarehouseService warehouse;

    public FieldWriteOffService(InventoryStore store, WarehouseService warehouse) {
        this.store = Objects.requireNonNull(store, "store e obrigatorio");
        this.warehouse = Objects.requireNonNull(warehouse, "servico de almoxarifado e obrigatorio");
    }

    /** Forma da porta da ED-20 (StockWriteOffPort): exige o local-base fiado na integracao. */
    public WriteOffResult writeOffDetailed(UUID tenant, String taskId, List<MaterialUse.Line> lines,
                                           String locationCode, UUID actor, Instant at) {
        Objects.requireNonNull(tenant, "tenant e obrigatorio");
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("task_id e obrigatorio (ADR-0032)");
        Objects.requireNonNull(lines, "linhas de material sao obrigatorias");
        if (store.fieldTaskProcessed(tenant, taskId)) {
            warehouse.audit(tenant, "FIELD_WRITE_OFF_DUPLICATE_IGNORED", actor, taskId, "reenvio idempotente: nenhuma baixa nova", false, at);
            return new WriteOffResult(taskId, true, List.of());
        }
        // valida TODAS as linhas ANTES de baixar qualquer uma (correcao M2 da
        // revisao ED-58): catalogo E quantidade > 0. A ED-20 (MaterialUse.Line)
        // aceita qty 0; aqui qty 0 e invalida e recusa a operacao INTEIRA —
        // sem baixa parcial, sem marcar a tarefa — de modo que o reenvio
        // valido da mesma task_id processa uma unica vez (idempotente).
        for (MaterialUse.Line l : lines) warehouse.requireItem(tenant, l.materialCode());
        for (MaterialUse.Line l : lines) {
            if (l.usedQty() == null || l.usedQty().signum() <= 0)
                throw new InventoryExceptions.ValidationException("FIELD_LINE_QTY_INVALID",
                        "baixa de campo exige quantidade usada > 0 em todas as linhas: "
                                + l.materialCode() + " veio " + l.usedQty() + " (operacao recusada sem baixar nada)");
        }
        List<WriteOffLineResult> out = new ArrayList<>();
        for (MaterialUse.Line l : lines) {
            InventoryItem.Item item = warehouse.requireItem(tenant, l.materialCode());
            StockBalance b = store.balance(tenant, l.materialCode(), locationCode);
            BigDecimal written = b.applyAutomaticWriteOff(l.usedQty());
            if (written.signum() > 0)
                store.appendMovement(new StockMovement(UUID.randomUUID(), tenant, StockMovement.Type.FIELD_WRITE_OFF,
                        l.materialCode(), locationCode, written, null, "TASK " + taskId, actor, "baixa de campo (ED-20)", at));
            BigDecimal shortfall = l.usedQty().subtract(written);
            if (shortfall.signum() > 0) warehouse.registerShortfall(tenant, l.materialCode(), locationCode, shortfall, "TASK " + taskId, at);
            boolean posterior = false;
            if (item.controlled()) {
                store.savePosteriorApproval(new InventoryStore.PosteriorApproval(UUID.randomUUID(), tenant, taskId,
                        l.materialCode(), l.usedQty(), InventoryStore.PosteriorApproval.Status.PENDING, at, null, null));
                warehouse.alert(tenant, InventoryPorts.AlertKind.POSTERIOR_APPROVAL_PENDING, l.materialCode(), locationCode,
                        "item controlado consumido em campo aguarda alcada a posteriori (task " + taskId + ")", at);
                posterior = true;
            }
            store.addTaskConsumption(new InventoryStore.TaskConsumption(tenant, taskId, null, l.materialCode(),
                    l.usedQty(), item.kind() == InventoryItem.Kind.PPE, at));
            warehouse.refreshNegative(tenant, b, at);
            warehouse.checkReorder(tenant, b, at);
            out.add(new WriteOffLineResult(l.materialCode(), l.usedQty(), written, shortfall, item.controlled(), posterior));
        }
        store.markFieldTaskProcessed(tenant, taskId);
        warehouse.audit(tenant, "FIELD_WRITE_OFF", actor, taskId, "linhas=" + out.size(), false, at);
        return new WriteOffResult(taskId, false, List.copyOf(out));
    }

    /** Alcada a posteriori do item controlado consumido em campo. */
    public void decidePosteriorApproval(UUID tenant, UUID approvalId, UUID approverId, boolean approve, Instant at) {
        InventoryStore.PosteriorApproval current = store.posteriorApprovals(tenant).stream()
                .filter(a -> a.id().equals(approvalId)).findFirst()
                .orElseThrow(() -> new InventoryExceptions.NotFoundException("POSTERIOR_APPROVAL_NOT_FOUND", "alcada posterior nao encontrada: " + approvalId));
        if (current.status() != InventoryStore.PosteriorApproval.Status.PENDING)
            throw new IllegalStateException("alcada posterior ja decidida: " + current.status());
        InventoryStore.PosteriorApproval decided = new InventoryStore.PosteriorApproval(current.id(), tenant, current.taskId(),
                current.itemCode(), current.qty(),
                approve ? InventoryStore.PosteriorApproval.Status.APPROVED : InventoryStore.PosteriorApproval.Status.REJECTED,
                current.createdAt(), at, approverId);
        store.savePosteriorApproval(decided);
        if (!approve) {
            store.addPendency(new InventoryStore.ShortfallPendency(UUID.randomUUID(), tenant, current.itemCode(), null,
                    current.qty(), "alcada posterior REJEITADA da task " + current.taskId(), at));
        }
        warehouse.audit(tenant, approve ? "POSTERIOR_APPROVAL_GRANTED" : "POSTERIOR_APPROVAL_REJECTED",
                approverId, current.taskId(), "item=" + current.itemCode() + " qty=" + current.qty(), false, at);
    }
}
