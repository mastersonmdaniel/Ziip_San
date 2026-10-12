package br.com.sigsan.commercial.collection.domain;

import br.com.sigsan.commercial.collection.payment.PaymentClassifier.AdjustmentKind;
import br.com.sigsan.commercial.collection.payment.PaymentStatus;
import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pagamento arrecadado (um registro G do retorno, ou um pagamento lançado por outro canal).
 * {@code documentId}/{@code unitId} não têm FK de propósito (documento polimorfico). Nenhum dado
 * pessoal fica aqui: o código de barras identifica o documento, não a pessoa.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends TenantAwareEntity {

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "return_file_id")
    private UUID returnFileId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "pending_excess", nullable = false, precision = 14, scale = 2)
    private BigDecimal pendingExcess = BigDecimal.ZERO;

    @Column(name = "excess_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal excessAmount = BigDecimal.ZERO;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "credited_on")
    private LocalDate creditedOn;

    @Column(name = "capture_form", length = 1)
    private String captureForm;

    @Column(name = "payment_form")
    private Integer paymentForm;

    @Column(name = "barcode", length = 44)
    private String barcode;

    @Column(name = "collecting_branch", length = 8)
    private String collectingBranch;

    @Column(name = "authentication", length = 23)
    private String authentication;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private PaymentStatus previousStatus;

    @Column(name = "identified", nullable = false)
    private boolean identified;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_kind", nullable = false, length = 20)
    private AdjustmentKind adjustmentKind = AdjustmentKind.NONE;

    @Column(name = "adjustment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    @Column(name = "classified_at")
    private OffsetDateTime classifiedAt;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
}
