package br.com.ziipsan.intelligence.gis.catalog.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Material do catálogo (V13_1). Só leitura nesta frente: a tela da P1.27
 * escolhe o material do item, não cria material. legacy_only (fibrocimento,
 * ferro cinzento, ferro galvanizado) existe para o legado: feição nova com
 * ele é recusada pela validação de feição (GIS-03), não por esta tela.
 */
@Entity
@Table(name = "gis_material")
@Getter
@Setter
public class GisMaterial extends TenantAwareEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "family")
    private String family;

    @Column(name = "standard_ref")
    private String standardRef;

    @Column(name = "hazen_williams_c", precision = 6, scale = 2)
    private BigDecimal hazenWilliamsC;

    @Column(name = "manning_n", precision = 6, scale = 4)
    private BigDecimal manningN;

    @Column(name = "roughness_mm", precision = 8, scale = 3)
    private BigDecimal roughnessMm;

    @Column(name = "service_life_years")
    private Integer serviceLifeYears;

    @Column(name = "legacy_only", nullable = false)
    private boolean legacyOnly;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "active", nullable = false)
    private boolean active;
}
