package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Subcategoria e numero de economias da unidade. A categoria principal e o total de economias sao calculados a partir daqui.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unit_subcategories")
@Getter
@Setter
public class UnitSubcategory extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "subcategory_id", nullable = false)
    private UUID subcategoryId;

    @Column(name = "economies", nullable = false)
    private int economies;

    @Column(name = "private_units")
    private Integer privateUnits;

    @Column(name = "collective_units")
    private Integer collectiveUnits;

    /**
     * P1.3 (V3_12): vigência da linha de economias. A competência passada usa o
     * valor da época; vigência nova começa no primeiro dia da competência
     * (regra do serviço). O serviço sempre grava validFrom explícito.
     */
    @Column(name = "valid_from", nullable = false)
    private java.time.LocalDate validFrom = java.time.LocalDate.now();

    @Column(name = "valid_to")
    private java.time.LocalDate validTo;

}
