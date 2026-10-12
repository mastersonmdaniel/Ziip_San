package br.com.sigsan.commercial.billing.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tarifa (catalogo do tenant, sem semente). As vigencias ficam em {@link TariffVersionEntity}.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "tariffs")
@Getter
@Setter
public class TariffEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "calc_type", nullable = false, length = 20)
    private String calcType = "BLOCKS";

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
