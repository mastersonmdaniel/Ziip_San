package br.com.ziipsan.core.chemicals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas da ED-89 para os vizinhos — consome por interface, sem duplicar:
 * <ul>
 *   <li>{@link StockEntryPort} / {@link StockQueryPort} — almoxarifado da
 *       ED-58: a entrada conferida POSTA no estoque da ED-58 e o saldo
 *       disponivel e LIDO dela; a ED-89 nao mantem saldo proprio;</li>
 *   <li>{@link CleaningOrderPort} — OS recorrente de limpeza de
 *       reservatorio sobre o nucleo de OS da P1.31/backoffice da ED-36:
 *       a ED-89 pede a OS e recebe o id; nao cria motor de OS;</li>
 *   <li>{@link AuditSink} — trilha de auditoria (toda operacao, inclusive
 *       as recusas nomeadas do controle PF);</li>
 *   <li>{@link AlertSink} — alertas (estoque minimo, validade, licenca PF
 *       vencida, informacao periodica vencida, limpeza vencida).</li>
 * </ul>
 */
public final class ChemPorts {
    private ChemPorts() {}

    /** Entrada no almoxarifado da ED-58 (mesma semantica de WarehouseService.entry). */
    public interface StockEntryPort {
        void postEntry(UUID tenantId, String itemCode, String locationCode, BigDecimal qty,
                       BigDecimal unitCost, String reference);
    }

    /** Leitura de saldo da ED-58: fisico e disponivel do item no local. */
    public interface StockQueryPort {
        record StockView(BigDecimal physical, BigDecimal available) {}
        Optional<StockView> stock(UUID tenantId, String itemCode, String locationCode);
    }

    /** OS de limpeza de reservatorio (P1.31/ED-36): devolve o id da OS criada. */
    public interface CleaningOrderPort {
        UUID openCleaningOrder(UUID tenantId, String reservoirCode, LocalDate dueOn, String reason);
    }

    public record AuditEntry(UUID tenantId, String action, UUID actorId, String reference,
                             String detail, boolean denied, Instant at) {}
    public interface AuditSink { void append(AuditEntry entry); }

    public enum AlertKind { MIN_STOCK_COVERAGE, LOT_EXPIRY, PF_LICENCE_EXPIRED,
                            PF_PERIODIC_REPORT_OVERDUE, CLEANING_OVERDUE, RECONCILIATION_DIVERGENT }

    public record Alert(UUID tenantId, AlertKind kind, String reference, String detail, Instant at) {}
    public interface AlertSink { void raise(Alert alert); }
}
