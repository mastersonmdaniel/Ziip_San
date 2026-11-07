package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Vínculo ligação ↔ unidade consumidora atendida
 * ({@code service_connection_units}, GIS-05, P2.23, V3_14). Uma ligação
 * tem várias linhas em condomínio; uma unidade tem no máximo uma linha
 * por tipo de ligação vigente (a unicidade 1:1 é da ligação com o
 * imóvel, não com a unidade).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "service_connection_units")
@Getter
@Setter
public class ServiceConnectionUnit extends TenantAwareEntity {

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;
}
