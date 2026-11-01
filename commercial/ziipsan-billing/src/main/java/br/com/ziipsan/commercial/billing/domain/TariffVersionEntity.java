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
 * Versao (vigencia por data) de uma tarifa. Intervalos da mesma tarifa nao se sobrepoem (restricao de exclusao no banco). {@code socialMinChargeDiscountPct} nulo = a versao nao preve tarifa social.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariff_versions")
@Getter
@Setter
public class TariffVersionEntity extends TenantAwareEntity {

    @Column(name = "tariff_id", nullable = false)
    private UUID tariffId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "social_min_charge_discount_pct", precision = 5, scale = 2)
    private BigDecimal socialMinChargeDiscountPct;

    @Column(name = "regulatory_act", length = 200)
    private String regulatoryAct;

    @Column(name = "published_on")
    private LocalDate publishedOn;
}
