package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Ramo de atividade de uma linha de subcategoria da unidade (P0.16, V3_5): uma linha
 * por ramo, como as demais composicoes do modulo (unit_customers, unit_subcategories).
 * A FK para activity-branches nao existe porque o catalogo nao existe no backend;
 * activityBranchId e guardado como UUID, referencia pendente, como as demais
 * referencias de catalogo inexistente da unidade.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unit_subcategory_activity_branches")
@Getter
@Setter
public class UnitSubcategoryActivity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "subcategory_id", nullable = false)
    private UUID subcategoryId;

    @Column(name = "activity_branch_id", nullable = false)
    private UUID activityBranchId;

}
