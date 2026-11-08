package br.com.ziipsan.core.inventory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-58 para os vizinhos — consome por interface, sem duplicar:
 * <ul>
 *   <li>{@link SupplierLookup} — cadastro minimo de fornecedor da P3.31
 *       (SupplierRecord): a entrada por NF-e valida o emitente por
 *       DOCUMENTO (CNPJ). A pendencia O9-4 (contractorRef x supplierId da
 *       medicao/empreiteiras) NAO e fiada aqui: nenhum contractorRef e
 *       resolvido para supplierId nesta entrega;</li>
 *   <li>{@link AuditSink} — trilha de auditoria (toda operacao);</li>
 *   <li>{@link AlertSink} — alertas do painel do tenant e da aba Estoque
 *       da Central Ziip (negativos, reposicao, validade, falta em baixa).</li>
 * </ul>
 */
public final class InventoryPorts {
    private InventoryPorts() {}

    public interface SupplierLookup {
        /** Devolve o id do fornecedor (P3.31) pelo documento; vazio = nao cadastrado. */
        Optional<UUID> findSupplierIdByDocument(UUID tenantId, String document);
        boolean isActive(UUID tenantId, UUID supplierId);
    }

    public record AuditEntry(UUID tenantId, String action, UUID actorId, String reference,
                             String detail, boolean denied, Instant at) {}

    public interface AuditSink { void append(AuditEntry entry); }

    public enum AlertKind { NEGATIVE_BALANCE, REGULARIZATION_OVERDUE, REORDER_POINT, LOT_EXPIRY,
                            WRITE_OFF_SHORTFALL, POSTERIOR_APPROVAL_PENDING, NFE_DIVERGENCE }

    public record Alert(UUID tenantId, AlertKind kind, String itemCode, String locationCode,
                        String detail, Instant at) {}

    public interface AlertSink { void raise(Alert alert); }
}
