package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Localidade: primeiro nivel da hierarquia territorial da inscricao.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "localities")
@Getter
@Setter
public class Locality extends TenantAwareEntity {

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "municipality_ibge", length = 7)
    private String municipalityIbge;

    @Column(name = "headquarters", nullable = false)
    private boolean headquarters;

    @Column(name = "computerized", nullable = false)
    private boolean computerized;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

}
