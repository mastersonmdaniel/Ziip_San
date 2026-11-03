package br.com.ziipsan.intelligence.gis.catalog.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Classe de feição do GIS (V13_1): nível intermediário do catálogo (ex.: PIPE, VALVE,
 * PUMP_STATION). Usada aqui só para leitura (o formulário de tipo escolhe a classe) e
 * para validar que o tipo novo aponta para uma classe do tenant. Classes de fábrica
 * (origin FACTORY) vêm da V13_2 e da R__ da P1.27; o tenant ativa/desativa a classe
 * pela mesma regra dos tipos, mas a tela da P1.27 não cria classe (fora do aceite).
 */
@Entity
@Table(name = "gis_feature_class")
@Getter
@Setter
public class GisFeatureClass extends TenantAwareEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "topology_role", nullable = false)
    private String topologyRole;

    @Column(name = "geometry_type", nullable = false)
    private String geometryType;

    @Column(name = "is_isolating", nullable = false)
    private boolean isolating;

    @Column(name = "hydraulic_role")
    private String hydraulicRole;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "active", nullable = false)
    private boolean active;
}
