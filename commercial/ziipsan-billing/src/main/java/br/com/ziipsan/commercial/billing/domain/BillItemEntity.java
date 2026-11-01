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
 * Linha da tarifa de agua ou esgoto na conta: por categoria, por versao de tarifa e por linha (parcela fixa ou faixa). Reproduz o calculo sem reexecutar o motor.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bill_items")
@Getter
@Setter
public class BillItemEntity extends TenantAwareEntity {

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "service_kind", nullable = false, length = 5)
    private String serviceKind;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "subcategory_id")
    private UUID subcategoryId;

    @Column(name = "tariff_version_id", nullable = false)
    private UUID tariffVersionId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "proration_factor", nullable = false, precision = 7, scale = 4)
    private BigDecimal prorationFactor;

    @Column(name = "line_type", nullable = false, length = 7)
    private String lineType;

    @Column(name = "economies", nullable = false)
    private int economies;

    @Column(name = "social_economies", nullable = false)
    private int socialEconomies;

    @Column(name = "from_m3", nullable = false)
    private int fromM3;

    @Column(name = "to_m3")
    private Integer toM3;

    @Column(name = "price_per_m3", precision = 14, scale = 4)
    private BigDecimal pricePerM3;

    @Column(name = "consumption", nullable = false)
    private int consumption;

    @Column(name = "gross_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossValue;

    @Column(name = "discount_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountValue = new BigDecimal("0.00");

    @Column(name = "net_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal netValue;
}
