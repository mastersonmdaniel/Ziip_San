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
 * Devolucao de cashback CBS/IBS concedida como credito na fatura (V4_12, P2.34). A constraint uq_cashback_return_domicile_period garante UMA unica devolucao por domicilio e periodo de afericao, mesmo com varias unidades na familia.
 *
 * <p>Nao usar @Builder: o Lombok descarta os campos herdados de BaseEntity.
 */
@Entity
@Table(name = "cashback_returns")
@Getter
@Setter
public class CashbackReturnEntity extends TenantAwareEntity {
    @Column(name = "domicile_key_hash", nullable = false, length = 64)
    private String domicileKeyHash;

    @Column(name = "assessment_month", nullable = false)
    private LocalDate assessmentMonth;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "bill_reference_month", nullable = false)
    private LocalDate billReferenceMonth;

    @Column(name = "list_import_id", nullable = false)
    private UUID listImportId;

    @Column(name = "list_entry_id", nullable = false)
    private UUID listEntryId;

    @Column(name = "cbs_tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cbsTaxAmount;

    @Column(name = "ibs_tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal ibsTaxAmount;

    @Column(name = "cbs_return_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cbsReturnAmount;

    @Column(name = "ibs_return_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal ibsReturnAmount;

    @Column(name = "total_return_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalReturnAmount;

    @Column(name = "tax_event_date", nullable = false)
    private LocalDate taxEventDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
