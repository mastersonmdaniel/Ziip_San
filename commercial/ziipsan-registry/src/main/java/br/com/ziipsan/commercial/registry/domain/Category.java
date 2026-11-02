package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Categoria de uso (catalogo do tenant, sem semente).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Abreviacao do frontend (catalogDefinitions.ts); opcional no backend. */
    @Column(name = "abbreviation", length = 5)
    private String abbreviation;

    /** Consumo minimo (m3) do frontend; opcional no backend. */
    @Column(name = "minimum_consumption")
    private Integer minimumConsumption;

    /** Limite de economias com confirmacao, do frontend; opcional no backend. */
    @Column(name = "economies_soft_limit")
    private Integer economiesSoftLimit;

    /** Exige permissao especial, do frontend. */
    @Column(name = "requires_special_permission", nullable = false)
    private boolean requiresSpecialPermission;

    @Column(name = "public_entity", nullable = false)
    private boolean publicEntity;

    @Column(name = "active", nullable = false)
    private boolean active;

}
