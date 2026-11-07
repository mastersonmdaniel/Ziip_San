package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Revisao aberta de uma conta (P2.20): uma linha por revisao, em
 * {@code bill_reviews}. Enquanto {@code status = OPEN} e dentro do teto
 * {@code bill.review_max_days}, os efeitos de
 * {@code bill.review_effects} ficam suspensos (ver
 * {@code ReviewSuspensionRule}). No maximo uma revisao aberta por conta
 * (indice unico parcial da V4_16).
 *
 * <p>Nao usar {@code @Builder}.
 */
@Entity
@Table(name = "bill_reviews")
@Getter
@Setter
public class BillReviewEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_reference_month", nullable = false)
    private LocalDate billReferenceMonth;

    @Column(name = "review_reason_id", nullable = false)
    private UUID reviewReasonId;

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "protocol_id")
    private UUID protocolId;

    /** OPEN ou CLOSED. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    /** opened_on + bill.review_max_days, fotografado na abertura. */
    @Column(name = "deadline_on", nullable = false)
    private LocalDate deadlineOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "note", nullable = false, length = 500)
    private String note;
}
