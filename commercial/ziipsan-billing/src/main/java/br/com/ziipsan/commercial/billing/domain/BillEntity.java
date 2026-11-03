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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Conta. {@code status} guarda um valor de {@code BillStatus}; no maximo uma conta viva por (unidade, referencia), garantido por indice unico parcial. {@code referenceMonth} e o primeiro dia do mes de competencia.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bills")
@Getter
@Setter
public class BillEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "water_consumption")
    private Integer waterConsumption;

    @Column(name = "sewer_volume")
    private Integer sewerVolume;

    @Column(name = "sewer_pct", precision = 7, scale = 4)
    private BigDecimal sewerPct;

    @Column(name = "collection_pct", precision = 7, scale = 4)
    private BigDecimal collectionPct;

    @Column(name = "water_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal waterValue;

    @Column(name = "sewer_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal sewerValue;

    @Column(name = "debits_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal debitsValue;

    @Column(name = "credits_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditsValue;

    @Column(name = "taxes_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxesValue;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "original_due_date")
    private LocalDate originalDueDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "rectification_count", nullable = false)
    private int rectificationCount;

    @Column(name = "due_change_count", nullable = false)
    private int dueChangeCount;

    @Column(name = "late_fee_charged", nullable = false)
    private boolean lateFeeCharged;

    @Column(name = "review_reason", length = 100)
    private String reviewReason;

    @Column(name = "reviewed_on")
    private LocalDate reviewedOn;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "origin_bill_id")
    private UUID originBillId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tariff_snapshot", columnDefinition = "jsonb")
    private String tariffSnapshot;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    // V4_6 (P1.16): tratamento do tributo no total. So WITHHOLDING_ONLY existe: o
    // total da conta subtrai apenas imposto retido; IBS/CBS nao entram no total ate a
    // P2.6 decidir o regime (dentro ou fora do preco) em migration propria.
    @Column(name = "tax_treatment", nullable = false, length = 20)
    private String taxTreatment = "WITHHOLDING_ONLY";

    // V4_6 (base para a P2.3): lote de auditoria da execucao que gerou a conta.
    @Column(name = "audit_batch_id")
    private UUID auditBatchId;
}
