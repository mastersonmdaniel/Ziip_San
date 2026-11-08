package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.inventory.InventoryItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico da ED-89: cadastro do produto quimico sobre a ED-58,
 * recebimento por NF-e com conferencia, consumo diario com conciliacao
 * e os portoes do controle PF no recebimento. TODA operacao grava
 * auditoria; alertas vao para a lista do store (painel do tenant).
 *
 * <p>Regras duras:
 * <ul>
 *   <li>nenhuma dosagem, tolerancia, cobertura ou periodicidade nasce
 *       aqui: tudo vem de {@link ChemParameters} ou do lancamento
 *       declarado — ausencia recusa com o nome da chave;</li>
 *   <li>saldo e custo sao da ED-58: este servico le pela porta e posta
 *       entrada pela porta, nunca mantem saldo proprio;</li>
 *   <li>produto controlado (controle PF ligado) nao recebe com a
 *       licenca vencida ({@link PfControl#requireReceiptAllowed}).</li>
 * </ul>
 */
public final class ChemService {

    private final ChemStore store;
    private final ChemParameters parameters;
    private final ChemPorts.StockEntryPort stockEntry;

    public ChemService(ChemStore store, ChemParameters parameters, ChemPorts.StockEntryPort stockEntry) {
        this.store = Objects.requireNonNull(store, "store e obrigatorio");
        this.parameters = Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
        this.stockEntry = Objects.requireNonNull(stockEntry, "porta de estoque (ED-58) e obrigatoria");
    }

    public ChemStore store() { return store; }
    public ChemParameters parameters() { return parameters; }

    /** Cadastro do produto, conferido contra o item REAL da ED-58 informado pela fiacao. */
    public void registerProduct(ChemProduct product, InventoryItem.Item inventoryItem, UUID actor, Instant at) {
        ChemProduct.requireMatchingItem(product, inventoryItem);
        if (!inventoryItem.active())
            throw new ChemExceptions.ValidationException("ITEM_INACTIVE", "item da ED-58 inativo: " + inventoryItem.code());
        store.putProduct(product);
        audit(product.tenantId(), "CHEM_PRODUCT_REGISTERED", actor, product.inventoryItemCode(),
                product.name() + " armazenamento=" + product.storageKind() + "@" + product.storageLocationCode(), false, at);
    }

    /**
     * Recebimento por NF-e com conferencia (ver {@link ChemReceipt}).
     * Antes de postar, aplica o portao PF por linha: produto controlado
     * com licenca vencida bloqueia o recebimento INTEIRO (nada posta
     * pela metade).
     */
    public ChemReceipt.ReceiptResult receiveFromNfe(UUID tenant, String nfeXml,
                                                    Map<String, BigDecimal> expectedByCode,
                                                    Map<String, ChemReceipt.LotReceipt> lotInfoByProductCode,
                                                    UUID actor, Instant at) {
        LocalDate today = LocalDate.ofInstant(at, java.time.ZoneOffset.UTC);
        if (lotInfoByProductCode != null) {
            for (String itemCode : lotInfoByProductCode.keySet()) {
                ChemProduct product = requireProduct(tenant, itemCode);
                if (!product.active())
                    throw new ChemExceptions.ValidationException("PRODUCT_INACTIVE", "produto inativo: " + itemCode);
                try {
                    PfControl.requireReceiptAllowed(store, tenant, itemCode, today);
                } catch (ChemExceptions.ValidationException e) {
                    audit(tenant, "CHEM_RECEIPT_DENIED", actor, itemCode, e.getMessage(), true, at);
                    alert(tenant, ChemPorts.AlertKind.PF_LICENCE_EXPIRED, itemCode, e.getMessage(), at);
                    throw e;
                }
            }
        }
        // Sem local unico da NF-e (correcao R3 da revisao da ED-89):
        // o local e o declarado POR LOTE em cada LotReceipt e e
        // respeitado por ChemReceipt linha a linha.
        ChemReceipt.ReceiptResult result = ChemReceipt.receive(store, stockEntry, tenant, nfeXml,
                expectedByCode, lotInfoByProductCode, at);
        audit(tenant, result.duplicate() ? "CHEM_RECEIPT_DUPLICATE" : "CHEM_RECEIPT_POSTED", actor,
                result.receipt() == null ? "?" : result.receipt().nfeAccessKey(),
                "lotes=" + (result.receipt() == null ? 0 : result.receipt().lots().size()), false, at);
        return result;
    }

    /**
     * Lanca o consumo diario e devolve a conciliacao. A tolerancia e a
     * do tenant; ausente, o status e REPORTED (sem veredito presumido).
     * Divergencia com tolerancia configurada gera alerta.
     */
    public DailyConsumption.Reconciliation recordConsumption(DailyConsumption consumption, UUID actor, Instant at) {
        requireProduct(consumption.tenantId(), consumption.itemCode());
        BigDecimal tolerance = parameters.reconciliationTolerancePct(consumption.tenantId()).orElse(null);
        DailyConsumption.Reconciliation reconciliation = consumption.reconcile(tolerance);
        store.saveConsumption(consumption);
        if (reconciliation.status() == DailyConsumption.Status.DIVERGENT)
            alert(consumption.tenantId(), ChemPorts.AlertKind.RECONCILIATION_DIVERGENT, consumption.itemCode(),
                    "consumo real " + consumption.actualQty() + " x teorico " + reconciliation.theoreticalQty()
                            + " (variacao " + reconciliation.variancePct() + "%) em " + consumption.consumptionDate(), at);
        audit(consumption.tenantId(), "CHEM_CONSUMPTION_RECORDED", actor, consumption.itemCode(),
                consumption.consumptionDate() + " real=" + consumption.actualQty() + " teorico=" + reconciliation.theoreticalQty()
                        + " status=" + reconciliation.status(), false, at);
        return reconciliation;
    }

    /**
     * Liga o controle PF do tenant pelos parametros DESTE servico
     * (correcao R1 da revisao da ED-89): a leitura explicita da chave
     * {@code chem.pf_control_enabled} acontece dentro de
     * {@link PfControl#enable} como primeiro portao.
     */
    public PfControl.PfState enablePfControl(UUID tenant, String currentListVersion, LocalDate today, Instant at) {
        return PfControl.enable(store, parameters, tenant, currentListVersion, today, at);
    }

    public ChemProduct requireProduct(UUID tenant, String itemCode) {
        return store.product(tenant, itemCode).orElseThrow(() ->
                new ChemExceptions.NotFoundException("CHEM_PRODUCT_NOT_FOUND", "produto quimico nao cadastrado: " + itemCode));
    }

    void audit(UUID tenant, String action, UUID actor, String reference, String detail, boolean denied, Instant at) {
        store.appendAudit(new ChemPorts.AuditEntry(tenant, action, actor, reference, detail, denied, at));
    }
    void alert(UUID tenant, ChemPorts.AlertKind kind, String reference, String detail, Instant at) {
        store.appendAlert(new ChemPorts.Alert(tenant, kind, reference, detail, at));
    }

    /** Lotes do tenant (leitura para relatorios de validade). */
    public List<ChemReceipt.LotReceipt> lots(UUID tenant, String itemCode) { return store.lots(tenant, itemCode); }
}
