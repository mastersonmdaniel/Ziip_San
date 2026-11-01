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
 * Faixa de volume {@code (fromM3, toM3]} com desconto da tarifa social. Percentual e limite de volume sao parametros da tabela tarifaria do tenant.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariff_social_bands")
@Getter
@Setter
public class TariffSocialBandEntity extends TenantAwareEntity {

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "from_m3", nullable = false)
    private int fromM3;

    @Column(name = "to_m3")
    private Integer toM3;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct;
}
