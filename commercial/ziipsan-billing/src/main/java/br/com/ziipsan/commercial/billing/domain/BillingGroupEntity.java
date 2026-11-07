package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Grupo de faturamento (V4_3, P2.5/F1): o conjunto de unidades faturado junto numa
 * referencia, sobre uma rota de leitura. {@code routeCode} e o identificador da rota
 * entregue a fundacao de lote da P2.1 quando o faturamento do grupo e despachado
 * (uma execucao por tenant e rota).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "billing_groups")
@Getter
@Setter
public class BillingGroupEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "route_code", nullable = false, length = 40)
    private String routeCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
