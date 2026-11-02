package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Subcategoria de uso, dentro de uma categoria.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "subcategories")
@Getter
@Setter
public class Subcategory extends TenantAwareEntity {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Abreviacao do frontend (catalogDefinitions.ts); opcional no backend. */
    @Column(name = "abbreviation", length = 20)
    private String abbreviation;

    /** Fator de fiscalizacao do frontend; opcional no backend. */
    @Column(name = "inspection_factor")
    private Integer inspectionFactor;

    /** Abastecimento sazonal, do frontend. */
    @Column(name = "seasonal_supply", nullable = false)
    private boolean seasonalSupply;

    @Column(name = "active", nullable = false)
    private boolean active;

}
