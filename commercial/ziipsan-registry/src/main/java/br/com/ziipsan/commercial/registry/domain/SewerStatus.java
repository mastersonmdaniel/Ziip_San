package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Situacao da ligacao de esgoto, com flags de comportamento (catalogo do tenant, sem semente).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "sewer_statuses")
@Getter
@Setter
public class SewerStatus extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "bills", nullable = false)
    private boolean bills;

    @Column(name = "requires_network", nullable = false)
    private boolean requiresNetwork;

    @Column(name = "connected", nullable = false)
    private boolean connected;

    @Column(name = "minimum_consumption", nullable = false)
    private boolean minimumConsumption;

    @Column(name = "active", nullable = false)
    private boolean active;

}
