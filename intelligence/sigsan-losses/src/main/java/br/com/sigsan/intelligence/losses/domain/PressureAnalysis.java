package br.com.sigsan.intelligence.losses.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pressure_analysis", indexes = {
        @Index(name = "idx_pressure_dma_time", columnList = "dma_id, timestamp"),
        @Index(name = "idx_pressure_time", columnList = "timestamp"),
        @Index(name = "idx_pressure_anomaly", columnList = "is_anomaly")
})
@Getter
@Setter
public class PressureAnalysis extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dma_id")
    private DmaDistrict dmaDistrict;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "avg_pressure_m", precision = 8, scale = 2)
    private BigDecimal avgPressureM;

    @Column(name = "min_pressure_m", precision = 8, scale = 2)
    private BigDecimal minPressureM;

    @Column(name = "max_pressure_m", precision = 8, scale = 2)
    private BigDecimal maxPressureM;

    @Column(name = "pressure_variance", precision = 8, scale = 4)
    private BigDecimal pressureVariance;

    @Column(name = "pressure_std_dev", precision = 8, scale = 4)
    private BigDecimal pressureStdDev;

    @Column(name = "transients_count")
    private Integer transientsCount;

    @Column(name = "negative_pressure_events")
    private Integer negativePressureEvents;

    @Column(name = "avg_flow_lps", precision = 10, scale = 3)
    private BigDecimal avgFlowLps;

    @Column(name = "night_flow_lps", precision = 10, scale = 3)
    private BigDecimal nightFlowLps;

    @Column(name = "minimum_night_flow_lps", precision = 10, scale = 3)
    private BigDecimal minimumNightFlowLps;

    @Column(name = "background_leakage_lps", precision = 10, scale = 3)
    private BigDecimal backgroundLeakageLps;

    @Column(name = "burst_leakage_lps", precision = 10, scale = 3)
    private BigDecimal burstLeakageLps;

    @Column(name = "is_anomaly")
    private Boolean isAnomaly = false;

    @Column(name = "anomaly_score", precision = 5, scale = 4)
    private BigDecimal anomalyScore;

    @Column(name = "anomaly_type", length = 50)
    private String anomalyType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;
}