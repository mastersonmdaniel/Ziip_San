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
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_credits")
@Getter
@Setter
public class BillCreditEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "receivable_id", nullable = false)
    private UUID receivableId;

    @Column(name = "origin", nullable = false, length = 40)
    private String origin;

    @Column(name = "installment_no")
    private Integer installmentNo;

    @Column(name = "applied_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "residual_after", nullable = false, precision = 14, scale = 2)
    private BigDecimal residualAfter;
}
