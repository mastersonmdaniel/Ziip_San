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
 * Faixa progressiva de uma regra de categoria. {@code toM3} nulo = faixa aberta (so a ultima).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariff_blocks")
@Getter
@Setter
public class TariffBlockEntity extends TenantAwareEntity {

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "block_order", nullable = false)
    private short blockOrder;

    @Column(name = "to_m3")
    private Integer toM3;

    @Column(name = "price_per_m3", nullable = false, precision = 14, scale = 4)
    private BigDecimal pricePerM3;
}
