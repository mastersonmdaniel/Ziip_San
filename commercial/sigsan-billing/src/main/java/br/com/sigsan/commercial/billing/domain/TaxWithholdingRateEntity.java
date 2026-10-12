package br.com.sigsan.commercial.billing.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Tributo retido e aliquota, parametro do tenant com vigencia (sem semente). O ultimo da ordem absorve o residuo de arredondamento.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tax_withholding_rates")
@Getter
@Setter
public class TaxWithholdingRateEntity extends TenantAwareEntity {

    @Column(name = "tax_code", nullable = false, length = 20)
    private String taxCode;

    @Column(name = "rate_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal ratePct;

    @Column(name = "apply_order", nullable = false)
    private short applyOrder;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
