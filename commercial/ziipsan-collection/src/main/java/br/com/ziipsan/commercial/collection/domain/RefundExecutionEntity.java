package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Efeito do encerramento da OS REFUND_REQUEST (P3.29, V6_5): UMA execucao
 * por devolucao e por OS (indices unicos na migration) - e essa unicidade
 * que faz o reenvio da OS nao duplicar credito nem devolucao.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "refund_executions")
@Getter
@Setter
public class RefundExecutionEntity extends TenantAwareEntity {

    @Column(name = "refund_request_id", nullable = false)
    private UUID refundRequestId;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    /** NEXT_BILL_CREDIT ou BANK_REFUND. */
    @Column(name = "effect", nullable = false, length = 24)
    private String effect;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "credited_bill_id")
    private UUID creditedBillId;

    @Column(name = "bank_reference", length = 80)
    private String bankReference;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
}
