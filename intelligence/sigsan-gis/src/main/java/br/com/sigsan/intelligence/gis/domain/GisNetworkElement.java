package br.com.sigsan.intelligence.gis.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import br.com.sigsan.intelligence.losses.domain.DmaDistrict;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "gis_network_element", indexes = {
        @Index(name = "idx_gis_elem_type", columnList = "element_type"),
        @Index(name = "idx_gis_elem_coords", columnList = "geometry"),
        @Index(name = "idx_gis_elem_dma", columnList = "dma_id"),
        @Index(name = "idx_gis_elem_status", columnList = "status")
})
@Getter
@Setter
public class GisNetworkElement extends TenantAwareEntity {

    @Column(name = "element_code", nullable = false, length = 50, unique = true)
    private String elementCode;

    @Column(name = "name", length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, length = 30)
    private ElementType elementType;

    @Column(name = "geometry", columnDefinition = "geometry(Geometry, 4674)", nullable = false)
    private org.locationtech.jts.geom.Geometry geometry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dma_id")
    private DmaDistrict dmaDistrict;

    @Column(name = "material", length = 50)
    private String material;

    @Column(name = "diameter_mm")
    private Integer diameterMm;

    @Column(name = "length_m", precision = 10, scale = 2)
    private BigDecimal lengthM;

    @Column(name = "installation_date")
    private OffsetDateTime installationDate;

    @Column(name = "age_years")
    private Integer ageYears;

    @Column(name = "depth_m", precision = 6, scale = 2)
    private BigDecimal depthM;

    @Column(name = "pressure_rating_bar", precision = 6, scale = 2)
    private BigDecimal pressureRatingBar;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "condition_rating", length = 20)
    private String conditionRating;

    @Column(name = "criticality", length = 20)
    private String criticality;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    @Column(name = "last_inspection_at")
    private OffsetDateTime lastInspectionAt;

    @Column(name = "next_inspection_due")
    private OffsetDateTime nextInspectionDue;

    public enum ElementType {
        // Água
        PIPE, VALVE, HYDRANT, METER, MACRO_METER, PRESSURE_REDUCING_VALVE,
        AIR_VALVE, BLOWOFF_VALVE, CHECK_VALVE, RESERVOIR, TANK, PUMP_STATION,
        TREATMENT_PLANT, WELL, SPRING, INTAKE, CONNECTION, SERVICE_LINE,

        // Esgoto
        SEWER_PIPE, MANHOLE, SEWER_VALVE, LIFT_STATION, SEWER_TREATMENT_PLANT,
        SEWER_CONNECTION, GREASE_TRAP, SIPHON, OVERFLOW_WEIR,

        // Comum
        NODE, JUNCTION, REDUCER, TEE, CROSS, CAP, FLANGE, COUPLING,
        SUPPORT, ANCHOR, THRUST_BLOCK, CATHODIC_PROTECTION, MONITORING_POINT;
    }
}