package br.com.sigsan.commercial.collection.domain;

import br.com.sigsan.commercial.collection.payment.PaymentStatus;
import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trilha da classificação de um pagamento (somente inserção): de qual situação para qual, por
 * quê e por qual execução de job.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "payment_status_events")
@Getter
@Setter
public class PaymentStatusEvent extends TenantAwareEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private PaymentStatus toStatus;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "job_run_id")
    private UUID jobRunId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
