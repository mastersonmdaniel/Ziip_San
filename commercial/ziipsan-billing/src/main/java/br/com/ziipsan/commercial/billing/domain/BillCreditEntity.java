package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Credito a realizar abatido da conta. {@code installmentNo} nulo = so se realizou residuo de parcelas ja vencidas.
 *
 * <p>DAD-20 (P2.5, trecho F3 da V4_4): {@code origin} em texto livre vira a referencia ao
 * catalogo {@code credit_origins} ({@code creditOriginId}) mais a fotografia do codigo
 * ({@code originCode}).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_credits")
@Getter
@Setter
public class BillCreditEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    // V4_14 (P2.3): mes da conta, exigido pela FK para a tabela particionada.
    // Preenchido pelo trigger trg_bill_credits_fill_month a partir da conta.
    @Column(name = "reference_month", nullable = false)
    private java.time.LocalDate referenceMonth;

    @Column(name = "receivable_id", nullable = false)
    private UUID receivableId;

    @Column(name = "credit_origin_id", nullable = false)
    private UUID creditOriginId;

    @Column(name = "origin_code", nullable = false, length = 40)
    private String originCode;

    @Column(name = "installment_no")
    private Integer installmentNo;

    @Column(name = "applied_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "residual_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal residualAfter;
}
