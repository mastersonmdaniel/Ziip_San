package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Requisicao de material gerada pela selecao de material na OS (ED-36
 * registra previsto x usado; a requisicao com alcada, a reserva e a baixa
 * sao DESTA entrega — ADR-0032). O valor de cada linha e a quantidade x
 * custo medio NO MOMENTO da criacao (retrato para a alcada; o custo
 * apropriado na OS no encerramento usa o medio do encerramento).
 */
public final class Requisition {

    public enum Status { PENDING_APPROVAL, APPROVED, REJECTED, DELIVERED, CLOSED, CANCELLED }
    public enum ApprovalMode { NONE, AUTO, HUMAN, POSTERIOR_PENDING }

    public record Line(String itemCode, BigDecimal qty, BigDecimal unitAvgCost) {
        public Line {
            if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item da requisicao e obrigatorio");
            if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("quantidade da requisicao deve ser positiva: " + itemCode);
            Objects.requireNonNull(unitAvgCost, "custo medio da linha e obrigatorio: " + itemCode);
        }
        public BigDecimal value() { return qty.multiply(unitAvgCost).setScale(2, RoundingMode.HALF_UP); }
    }

    private final UUID id;
    private final UUID tenantId;
    private final UUID orderId;
    private final UUID requesterId;
    private final String locationCode;
    private final List<Line> lines;
    private final Instant createdAt;
    private Status status = Status.PENDING_APPROVAL;
    private ApprovalMode approvalMode = ApprovalMode.NONE;
    private UUID approverId;
    private String approvalNote;
    private Instant decidedAt;

    public Requisition(UUID id, UUID tenantId, UUID orderId, UUID requesterId, String locationCode,
                       List<Line> lines, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id da requisicao e obrigatorio");
        this.tenantId = Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        this.orderId = Objects.requireNonNull(orderId, "OS da requisicao e obrigatoria");
        this.requesterId = Objects.requireNonNull(requesterId, "requisitante e obrigatorio");
        if (locationCode == null || locationCode.isBlank()) throw new IllegalArgumentException("local de estoque da requisicao e obrigatorio");
        this.locationCode = locationCode;
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("requisicao exige ao menos uma linha");
        this.lines = List.copyOf(lines);
        this.createdAt = Objects.requireNonNull(createdAt, "criacao da requisicao e obrigatoria");
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID orderId() { return orderId; }
    public UUID requesterId() { return requesterId; }
    public String locationCode() { return locationCode; }
    public List<Line> lines() { return lines; }
    public Instant createdAt() { return createdAt; }
    public Status status() { return status; }
    public ApprovalMode approvalMode() { return approvalMode; }
    public UUID approverId() { return approverId; }
    public String approvalNote() { return approvalNote; }
    public Instant decidedAt() { return decidedAt; }

    public BigDecimal totalValue() { return lines.stream().map(Line::value).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal qtyOf(String itemCode) {
        return lines.stream().filter(l -> l.itemCode().equals(itemCode)).map(Line::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void markApproved(ApprovalMode mode, UUID approverId, String note, Instant at) {
        requireStatus(Status.PENDING_APPROVAL, "aprovar");
        this.status = Status.APPROVED; this.approvalMode = mode; this.approverId = approverId;
        this.approvalNote = note; this.decidedAt = at;
    }
    void markRejected(UUID approverId, String note, Instant at) {
        requireStatus(Status.PENDING_APPROVAL, "rejeitar");
        this.status = Status.REJECTED; this.approverId = approverId; this.approvalNote = note; this.decidedAt = at;
    }
    void markDelivered() { requireStatus(Status.APPROVED, "entregar"); this.status = Status.DELIVERED; }
    void markClosed() { requireStatus(Status.DELIVERED, "fechar"); this.status = Status.CLOSED; }
    void markCancelled() {
        if (status != Status.PENDING_APPROVAL && status != Status.APPROVED)
            throw new IllegalStateException("cancelar requisicao em " + status + " nao e permitido (entregue fecha por encerramento/devolucao)");
        this.status = Status.CANCELLED;
    }
    private void requireStatus(Status expected, String op) {
        if (status != expected) throw new IllegalStateException("requisicao " + id + " em " + status + ": nao pode " + op);
    }
}
