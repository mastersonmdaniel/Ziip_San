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
 * Credito a realizar: valor a favor do cliente abatido da conta em parcelas. {@code residualAmount} e o que a conta anterior nao comportou e passa ao mes seguinte.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "credit_receivables")
@Getter
@Setter
public class CreditReceivableEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "origin", nullable = false, length = 40)
    private String origin;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "installments", nullable = false)
    private int installments;

    @Column(name = "installments_done", nullable = false)
    private int installmentsDone;

    @Column(name = "residual_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal residualAmount = new BigDecimal("0.00");

    @Column(name = "status", nullable = false, length = 12)
    private String status = "NORMAL";
}
