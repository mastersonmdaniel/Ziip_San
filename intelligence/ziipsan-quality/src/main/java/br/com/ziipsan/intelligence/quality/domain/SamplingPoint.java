package br.com.ziipsan.intelligence.quality.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sampling_point", indexes = {
        @Index(name = "idx_sampling_point_code", columnList = "code"),
        @Index(name = "idx_sampling_point_type", columnList = "type"),
        @Index(name = "idx_sampling_point_coords", columnList = "coordinates")
})
@Getter
@Setter
public class SamplingPoint extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30)
    private PointType type;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @Column(name = "coordinates", columnDefinition = "geometry(Point, 4674)")
    private org.locationtech.jts.geom.Point coordinates;

    @Column(name = "system_id")
    private UUID systemId;

    @Column(name = "sector_id")
    private UUID sectorId;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @Column(name = "sampling_frequency", length = 50)
    private String samplingFrequency;

    @Column(name = "collection_method", length = 100)
    private String collectionMethod;

    @Column(name = "preservation_method", length = 100)
    private String preservationMethod;

    @Column(name = "transport_conditions", columnDefinition = "text")
    private String transportConditions;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum PointType {
        RAW_WATER, TREATED_WATER, DISTRIBUTION_NETWORK, RESERVOIR, ETA_INLET, ETA_OUTLET,
        ETE_INLET, ETE_OUTLET, SLUDGE, REUSE, CONSUMER_TAP, HYDRANT, WELL, SPRING;
    }
}