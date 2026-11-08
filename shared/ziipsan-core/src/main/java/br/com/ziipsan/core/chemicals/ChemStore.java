package br.com.ziipsan.core.chemicals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistencia da ED-89. A implementacao de integracao
 * (JDBC/JPA sobre o SQL pendente ED-89) e da camada de integracao; aqui,
 * {@link InMemoryChemStore} sustenta os testes do nucleo puro — mesmo
 * desenho da ED-58 (InventoryStore + InMemoryInventoryStore).
 */
public interface ChemStore {

    // produtos e lotes recebidos
    void putProduct(ChemProduct product);
    Optional<ChemProduct> product(UUID tenantId, String itemCode);
    List<ChemProduct> products(UUID tenantId);

    void saveLotReceipt(ChemReceipt.LotReceipt lot);
    List<ChemReceipt.LotReceipt> lots(UUID tenantId, String itemCode);
    Optional<ChemReceipt.LotReceipt> lot(UUID tenantId, String itemCode, String lotCode);

    // recebimento por NF-e: idempotencia por chave de acesso (espelho ED-58)
    boolean receiptProcessed(UUID tenantId, String nfeAccessKey);
    void markReceiptProcessed(UUID tenantId, String nfeAccessKey);
    void saveReceipt(ChemReceipt.Receipt receipt);
    List<ChemReceipt.Receipt> receipts(UUID tenantId);

    // consumo diario e conciliacao
    void saveConsumption(DailyConsumption consumption);
    List<DailyConsumption> consumptions(UUID tenantId, String itemCode);

    // controle Policia Federal
    void savePfCheck(PfControl.PreEnableCheck check);
    Optional<PfControl.PreEnableCheck> latestPfCheck(UUID tenantId);
    /**
     * Conferencia de UMA versao da lista (correcao R2 da revisao da
     * ED-89): o portao PF casa contra a conferencia da versao
     * HABILITADA, nao contra a mais recente. Implementacao padrao:
     * serve a conferencia mais recente quando ela e da versao pedida;
     * stores com historico (ex.: {@link InMemoryChemStore}) sobrescrevem
     * para devolver a conferencia daquela versao mesmo apos conferencias
     * mais novas. No SQL pendente, e a linha de {@code chem_pf_checks}
     * do tenant com {@code list_version} igual a versao pedida.
     */
    default Optional<PfControl.PreEnableCheck> pfCheckForVersion(UUID tenantId, String listVersion) {
        if (listVersion == null) return Optional.empty();
        return latestPfCheck(tenantId).filter(c -> c.listVersion().equals(listVersion));
    }
    void savePfRegistration(PfControl.PfRegistration registration);
    List<PfControl.PfRegistration> pfRegistrations(UUID tenantId);
    void savePfLicence(PfControl.PfLicence licence);
    List<PfControl.PfLicence> pfLicences(UUID tenantId);
    void savePfState(PfControl.PfState state);
    Optional<PfControl.PfState> pfState(UUID tenantId);
    void savePeriodicReport(PfControl.PeriodicReport report);
    List<PfControl.PeriodicReport> periodicReports(UUID tenantId);

    // reservatorios e limpeza
    void putReservoir(ReservoirCleaning.Reservoir reservoir);
    Optional<ReservoirCleaning.Reservoir> reservoir(UUID tenantId, String code);
    List<ReservoirCleaning.Reservoir> reservoirs(UUID tenantId);
    void saveCleaningPlan(ReservoirCleaning.CleaningPlan plan);
    Optional<ReservoirCleaning.CleaningPlan> cleaningPlan(UUID tenantId, String reservoirCode);
    List<ReservoirCleaning.CleaningPlan> cleaningPlans(UUID tenantId);
    boolean cleaningOrderGenerated(UUID tenantId, String reservoirCode, LocalDate dueOn);
    void markCleaningOrderGenerated(UUID tenantId, String reservoirCode, LocalDate dueOn, UUID orderId);
    /**
     * Id da OS gerada para (reservatorio, vencimento) — correcao R4
     * da revisao da ED-89: {@code recordExecution} so aceita a OS que
     * foi gerada para aquele reservatorio e vencimento, com este id.
     * No SQL pendente, e o {@code order_id} de
     * {@code chem_cleaning_orders} (PK tenant_id, reservoir_id,
     * due_on). Implementacao padrao vazia = nenhuma OS comprovavel
     * (fail-closed); a integracao implementa a leitura.
     */
    default Optional<UUID> cleaningOrderId(UUID tenantId, String reservoirCode, LocalDate dueOn) {
        return Optional.empty();
    }
    void saveCleaningExecution(ReservoirCleaning.CleaningExecution execution);
    List<ReservoirCleaning.CleaningExecution> cleaningExecutions(UUID tenantId);

    // auditoria e alertas (leitura para relatorios)
    void appendAudit(ChemPorts.AuditEntry entry);
    List<ChemPorts.AuditEntry> audit(UUID tenantId);
    void appendAlert(ChemPorts.Alert alert);
    List<ChemPorts.Alert> alerts(UUID tenantId);
}
