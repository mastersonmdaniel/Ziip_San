package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Lote de auditoria de faturamento (V4_6, base para a P2.3): uma execucao de faturamento
 * ({@code runId}) por mes de referencia, com a quantidade de contas e o total apurado.
 * A P2.3 preenche estes lotes por execucao e prova a poda por particao; na P1 a tabela
 * so existe e {@code bills.audit_batch_id} e opcional.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_audit_batches")
@Getter
@Setter
public class BillAuditBatchEntity extends TenantAwareEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "bill_count", nullable = false)
    private int billCount;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue;
}
