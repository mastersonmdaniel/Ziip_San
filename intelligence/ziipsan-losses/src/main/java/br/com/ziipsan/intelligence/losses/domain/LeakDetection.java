package br.com.ziipsan.intelligence.losses.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "leak_detection", indexes = {
        @Index(name = "idx_leak_coords", columnList = "location"),
        @Index(name = "idx_leak_status", columnList = "status"),
        @Index(name = "idx_leak_detected", columnList = "detected_at"),
        @Index(name = "idx_leak_dma", columnList = "dma_id")
})
@Getter
@Setter
public class LeakDetection extends TenantAwareEntity {

    @Column(name = "detection_code", nullable = false, length = 50, unique = true)
    private String detectionCode;

    @Column(name = "location", columnDefinition = "geometry(Point, 4674)", nullable = false)
    private org.locationtech.jts.geom.Point location;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dma_id")
    private DmaDistrict dmaDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_method", length = 30)
    private DetectionMethod detectionMethod;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "leak_type", length = 30)
    private LeakType leakType;

    @Column(name = "estimated_flow_lps", precision = 10, scale = 3)
    private BigDecimal estimatedFlowLps;

    @Column(name = "estimated_daily_loss_m3", precision = 10, scale = 3)
    private BigDecimal estimatedDailyLossM3;

    @Column(name = "pipe_material", length = 50)
    private String pipeMaterial;

    @Column(name = "pipe_diameter_mm")
    private Integer pipeDiameterMm;

    @Column(name = "pipe_age_years")
    private Integer pipeAgeYears;

    @Column(name = "depth_m", precision = 6, scale = 2)
    private BigDecimal depthM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private LeakStatus status = LeakStatus.DETECTED;

    @Column(name = "priority", length = 20)
    private String priority = "MEDIUM";

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "detected_by")
    private UUID detectedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "repaired_at")
    private OffsetDateTime repairedAt;

    @Column(name = "repaired_by")
    private UUID repairedBy;

    @Column(name = "repair_cost", precision = 12, scale = 2)
    private BigDecimal repairCost;

    @Column(name = "repair_method", length = 100)
    private String repairMethod;

    @Column(name = "repair_duration_hours")
    private BigDecimal repairDurationHours;

    @Column(name = "water_saved_m3_day", precision = 10, scale = 3)
    private BigDecimal waterSavedM3Day;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "photos", columnDefinition = "jsonb")
    private String photos;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum DetectionMethod {
        ACOUSTIC_LOGGER, GROUND_MICROPHONE, CORRELATOR, GPR, THERMAL_CAMERA,
        SATELLITE_INSAR, DRONE_THERMAL, SMART_METER_ANALYSIS, PRESSURE_ANALYSIS,
        FLOW_ANALYSIS, CUSTOMER_REPORT, VISUAL_INSPECTION, TRACER_GAS;
    }

    public enum LeakType {
        SERVICE_CONNECTION, MAIN_PIPE, VALVE, FITTING, HYDRANT,
        METER, RESERVOIR, TREATMENT_PLANT, UNKNOWN;
    }

    public enum LeakStatus {
        DETECTED, CONFIRMED, SCHEDULED, IN_REPAIR, REPAIRED, MONITORING, FALSE_POSITIVE, CLOSED;
    }
}