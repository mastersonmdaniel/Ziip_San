package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Origem de credito do catalogo {@code credit_origins} (DAD-20, P2.5; classe a; trecho F3 da
 * V4_4). Substitui o {@code origin} em texto livre de {@code credit_receivables}. O catalogo
 * comeca vazio: origens sao referencia do produto/tenant, nunca dado inventado.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "credit_origins")
@Getter
@Setter
public class CreditOriginEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
