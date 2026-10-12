package br.com.sigsan.intelligence.losses.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "dma_district", indexes = {
        @Index(name = "idx_dma_code", columnList = "code"),
        @Index(name = "idx_dma_status", columnList = "status")
})
@Getter
@Setter
public class DmaDistrict extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "boundary", columnDefinition = "geometry(Polygon, 4674)")
    private Polygon boundary;

    @Column(name = "area_km2", precision = 10, scale = 4)
    private BigDecimal areaKm2;

    @Column(name = "population_served")
    private Integer populationServed;

    @Column(name = "connections_count")
    private Integer connectionsCount;

    @Column(name = "pipe_length_km", precision = 10, scale = 3)
    private BigDecimal pipeLengthKm;

    @Column(name = "avg_pressure_m", precision = 8, scale = 2)
    private BigDecimal avgPressureM;

    @Column(name = "min_pressure_m", precision = 8, scale = 2)
    private BigDecimal minPressureM;

    @Column(name = "max_pressure_m", precision = 8, scale = 2)
    private BigDecimal maxPressureM;

    @Column(name = "elevation_min_m", precision = 8, scale = 2)
    private BigDecimal elevationMinM;

    @Column(name = "elevation_max_m", precision = 8, scale = 2)
    private BigDecimal elevationMaxM;

    @Column(name = "input_meter_id")
    private UUID inputMeterId;

    @Column(name = "output_meter_id")
    private UUID outputMeterId;

    @Column(name = "parent_dma_id")
    private UUID parentDmaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private DmaStatus status = DmaStatus.ACTIVE;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum DmaStatus {
        ACTIVE, INACTIVE, PLANNING, UNDER_CONSTRUCTION, DECOMMISSIONED;
    }
}
