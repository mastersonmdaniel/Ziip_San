package br.com.sigsan.commercial.registry.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Endereco polimorfico (unidade, cliente...). O ponto geografico esta em SIRGAS 2000 (EPSG:4674).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
public class Address extends TenantAwareEntity {

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "cep", length = 10)
    private String cep;

    @Column(name = "street", length = 200)
    private String street;

    @Column(name = "number", length = 20)
    private String number;

    @Column(name = "complement", length = 100)
    private String complement;

    @Column(name = "neighborhood", length = 100)
    private String neighborhood;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "coordinates", columnDefinition = "geometry(Point, 4674)")
    private org.locationtech.jts.geom.Point coordinates;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public enum Type { PHYSICAL, BILLING, CORRESPONDENCE, ALTERNATIVE }

}
