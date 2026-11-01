package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Quadra: dentro de um setor. Diz se ha rede de agua e de esgoto, que condiciona as situacoes de ligacao das unidades.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "blocks")
@Getter
@Setter
public class Block extends TenantAwareEntity {

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "reading_route_id")
    private UUID readingRouteId;

    @Column(name = "dma_id")
    private UUID dmaId;

    @Column(name = "has_water_network", nullable = false)
    private boolean hasWaterNetwork;

    @Column(name = "has_sewer_network", nullable = false)
    private boolean hasSewerNetwork;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

}
