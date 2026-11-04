package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Rota de leitura.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "reading_routes")
@Getter
@Setter
public class ReadingRoute extends TenantAwareEntity {

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "locality_id")
    private UUID localityId;

    @Column(name = "sector_id")
    private UUID sectorId;

    @Column(name = "max_units")
    private Integer maxUnits;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** P1.3 (V3_12): grupo de faturamento da rota (referência pendente, sem FK). */
    @Column(name = "billing_group_id")
    private UUID billingGroupId;

}
