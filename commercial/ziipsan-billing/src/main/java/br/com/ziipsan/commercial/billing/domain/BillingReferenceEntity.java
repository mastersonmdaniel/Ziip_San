package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Referencia mensal do ciclo de faturamento (V4_3, P2.5/F1). {@code referenceMonth} e o
 * primeiro dia do mes de competencia, como em {@code bills}. {@code status} guarda um
 * valor de {@code BillingReferenceStatus}: OPEN admite comando de atividades e geracao
 * de contas; CLOSED e o encerramento do mes depois de todos os grupos, e nenhuma
 * geracao e admitida nela. {@code closedAt} existe exatamente quando CLOSED (CHECK no
 * banco).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "billing_references")
@Getter
@Setter
public class BillingReferenceEntity extends TenantAwareEntity {

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;
}
