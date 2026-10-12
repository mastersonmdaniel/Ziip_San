package br.com.sigsan.commercial.registry.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Setor comercial: dentro de uma localidade.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "commercial_sectors")
@Getter
@Setter
public class CommercialSector extends TenantAwareEntity {

    @Column(name = "locality_id", nullable = false)
    private UUID localityId;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

}
