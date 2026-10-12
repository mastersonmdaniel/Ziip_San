package br.com.sigsan.intelligence.quality.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quality_sample", indexes = {
        @Index(name = "idx_sample_code", columnList = "sample_code", unique = true),
        @Index(name = "idx_sample_point", columnList = "sampling_point_id"),
        @Index(name = "idx_sample_status", columnList = "status"),
        @Index(name = "idx_sample_collected", columnList = "collected_at"),
        @Index(name = "idx_sample_type", columnList = "sample_type")
})
@Getter
@Setter
public class QualitySample extends TenantAwareEntity {

    @Column(name = "sample_code", nullable = false, length = 50, unique = true)
    private String sampleCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sampling_point_id", nullable = false)
    private SamplingPoint samplingPoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_type", length = 30)
    private SampleType sampleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private SampleStatus status = SampleStatus.PROGRAMMED;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "collector_id")
    private UUID collectorId;

    @Column(name = "collector_name", length = 200)
    private String collectorName;

    @Column(name = "collection_method", length = 100)
    private String collectionMethod;

    @Column(name = "volume_ml")
    private Integer volumeMl;

    @Column(name = "container_type", length = 50)
    private String containerType;

    @Column(name = "preservative", length = 100)
    private String preservative;

    @Column(name = "temperature_celsius", precision = 5, scale = 2)
    private BigDecimal temperatureCelsius;

    @Column(name = "ph_field", precision = 4, scale = 2)
    private BigDecimal phField;

    @Column(name = "chlorine_residual_mg_l", precision = 6, scale = 3)
    private BigDecimal chlorineResidualMgL;

    @Column(name = "turbidity_ntu", precision = 8, scale = 3)
    private BigDecimal turbidityNtu;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "lab_id")
    private UUID labId;

    @Column(name = "lab_sample_code", length = 50)
    private String labSampleCode;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum SampleType {
        ROUTINE, SPECIAL, COMPLAINT, OPERATIONAL, RESEARCH, VALIDATION, AUDIT;
    }

    public enum SampleStatus {
        PROGRAMMED, COLLECTED, IN_TRANSIT, RECEIVED, IN_ANALYSIS, ANALYZED,
        VALIDATED, PUBLISHED, REJECTED, RECOLLECT, ARCHIVED;
    }
}