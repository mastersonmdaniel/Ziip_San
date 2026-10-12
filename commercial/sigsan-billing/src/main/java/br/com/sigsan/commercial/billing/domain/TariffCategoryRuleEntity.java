package br.com.sigsan.commercial.billing.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Regra de uma categoria (ou subcategoria) numa versao de tarifa: franquia, parcela fixa e fator de economias. As faixas ficam em {@link TariffBlockEntity}.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariff_category_rules")
@Getter
@Setter
public class TariffCategoryRuleEntity extends TenantAwareEntity {

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "subcategory_id")
    private UUID subcategoryId;

    @Column(name = "min_consumption", nullable = false)
    private int minConsumption;

    @Column(name = "min_charge", nullable = false, precision = 14, scale = 4)
    private BigDecimal minCharge;

    @Column(name = "economy_factor", precision = 8, scale = 4)
    private BigDecimal economyFactor;
}
