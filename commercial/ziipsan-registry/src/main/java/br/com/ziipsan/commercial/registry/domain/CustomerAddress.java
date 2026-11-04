package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligação própria cliente-endereço (P1.3, V3_12): substitui o par polimórfico
 * (entity_type/entity_id) para os vínculos NOVOS de cliente. A correspondência
 * tem vigência; no máximo uma correspondência vigente por cliente (índice
 * parcial da V3_12). As linhas legadas foram importadas pela migration.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
public class CustomerAddress extends TenantAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_correspondence", nullable = false)
    private boolean correspondence;

}
