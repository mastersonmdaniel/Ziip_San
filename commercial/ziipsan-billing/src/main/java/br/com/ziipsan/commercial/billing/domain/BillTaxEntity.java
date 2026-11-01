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
 * Imposto retido na conta (so existe se o tenant retem).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_taxes")
@Getter
@Setter
public class BillTaxEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "tax_code", nullable = false, length = 20)
    private String taxCode;

    @Column(name = "apply_order", nullable = false)
    private short applyOrder;

    @Column(name = "rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePct;

    @Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
