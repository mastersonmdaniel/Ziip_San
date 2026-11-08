package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistencia do almoxarifado. A implementacao de integracao
 * (JDBC/JPA sobre o SQL pendente ED-58) e da camada de integracao; aqui,
 * {@link InMemoryInventoryStore} sustenta os testes do nucleo puro —
 * mesmo desenho da P3.31 (GovernanceStore + InMemoryGovernanceStore).
 */
public interface InventoryStore {

    // catalogos e saldos
    void putLocation(UUID tenantId, InventoryItem.StockLocation location);
    Optional<InventoryItem.StockLocation> location(UUID tenantId, String code);
    void putItem(UUID tenantId, InventoryItem.Item item);
    Optional<InventoryItem.Item> item(UUID tenantId, String code);
    List<InventoryItem.Item> items(UUID tenantId);
    StockBalance balance(UUID tenantId, String itemCode, String locationCode);
    List<StockBalance> balances(UUID tenantId);

    // ledger e requisicoes
    void appendMovement(StockMovement movement);
    List<StockMovement> movements(UUID tenantId);
    void saveRequisition(Requisition requisition);
    Optional<Requisition> requisition(UUID tenantId, UUID id);
    List<Requisition> requisitionsByOrder(UUID tenantId, UUID orderId);

    // negativos: desde quando (item, local) esta com disponivel < 0
    void markNegativeSince(UUID tenantId, String itemCode, String locationCode, Instant since);
    void clearNegativeSince(UUID tenantId, String itemCode, String locationCode);
    Optional<Instant> negativeSince(UUID tenantId, String itemCode, String locationCode);

    // pendencias de regularizacao (falta em baixa automatica)
    record ShortfallPendency(UUID id, UUID tenantId, String itemCode, String locationCode,
                             BigDecimal missingQty, String reference, Instant at) {}
    void addPendency(ShortfallPendency pendency);
    List<ShortfallPendency> pendencies(UUID tenantId);

    // transferencias
    record Transfer(UUID id, UUID tenantId, String itemCode, BigDecimal qty,
                    String fromLocation, String toLocation, Status status, Instant createdAt, Instant closedAt) {
        public enum Status { IN_TRANSIT, RECEIVED, CANCELLED }
    }
    void saveTransfer(Transfer transfer);
    Optional<Transfer> transfer(UUID tenantId, UUID id);
    List<Transfer> transfers(UUID tenantId);

    // lotes
    void saveLot(LotControl.Lot lot);
    List<LotControl.Lot> lots(UUID tenantId);

    // kits por tipo de OS
    void saveKit(MaterialKit.Kit kit);
    Optional<MaterialKit.Kit> kit(UUID tenantId, String orderTypeCode);

    // baixa de campo idempotente (ED-20) e alcada posterior
    boolean fieldTaskProcessed(UUID tenantId, String taskId);
    void markFieldTaskProcessed(UUID tenantId, String taskId);
    record PosteriorApproval(UUID id, UUID tenantId, String taskId, String itemCode,
                             BigDecimal qty, Status status, Instant createdAt, Instant decidedAt, UUID decidedBy) {
        public enum Status { PENDING, APPROVED, REJECTED }
    }
    void savePosteriorApproval(PosteriorApproval approval);
    List<PosteriorApproval> posteriorApprovals(UUID tenantId);

    // entrada por NF-e idempotente por chave de acesso (correcao M3 da revisao ED-58)
    boolean nfeEntryProcessed(UUID tenantId, String accessKey);
    void markNfeEntryProcessed(UUID tenantId, String accessKey);

    // evidencia por tarefa (ISO 45001 / ED-71)
    record TaskConsumption(UUID tenantId, String taskId, UUID orderId, String itemCode,
                           BigDecimal qty, boolean ppe, Instant at) {}
    void addTaskConsumption(TaskConsumption consumption);
    List<TaskConsumption> taskConsumptions(UUID tenantId);

    // auditoria e alertas (leitura para relatorios/Central)
    void appendAudit(InventoryPorts.AuditEntry entry);
    List<InventoryPorts.AuditEntry> audit(UUID tenantId);
    void appendAlert(InventoryPorts.Alert alert);
    List<InventoryPorts.Alert> alerts(UUID tenantId);

    // contagens de inventario
    record CountLine(String itemCode, BigDecimal systemQty, BigDecimal countedQty) {
        public BigDecimal difference() { return countedQty.subtract(systemQty); }
    }
    record InventoryCount(UUID id, UUID tenantId, String locationCode, List<CountLine> lines,
                          String justification, UUID actorId, Instant at) {}
    void saveCount(InventoryCount count);
    List<InventoryCount> counts(UUID tenantId);
}
