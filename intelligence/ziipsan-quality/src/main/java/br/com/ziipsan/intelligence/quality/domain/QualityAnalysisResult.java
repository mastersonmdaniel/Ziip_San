package br.com.ziipsan.intelligence.quality.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quality_analysis_result", indexes = {
        @Index(name = "idx_analysis_sample", columnList = "sample_id"),
        @Index(name = "idx_analysis_parameter", columnList = "parameter_id"),
        @Index(name = "idx_analysis_status", columnList = "status"),
        @Index(name = "idx_analysis_analyzed", columnList = "analyzed_at"),
        @Index(name = "idx_analysis_conformity", columnList = "conformity")
})
@Getter
@Setter
public class QualityAnalysisResult extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sample_id", nullable = false)
    private QualitySample sample;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    private QualityParameter parameter;

    @Column(name = "result_value", precision = 12, scale = 6)
    private BigDecimal resultValue;

    @Column(name = "result_text", length = 500)
    private String resultText;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "method_code", length = 50)
    private String methodCode;

    @Column(name = "method_description", columnDefinition = "text")
    private String methodDescription;

    @Column(name = "detection_limit", precision = 12, scale = 6)
    private BigDecimal detectionLimit;

    @Column(name = "quantification_limit", precision = 12, scale = 6)
    private BigDecimal quantificationLimit;

    @Column(name = "uncertainty", precision = 8, scale = 4)
    private BigDecimal uncertainty;

    @Column(name = "vmp", precision = 10, scale = 4)
    private BigDecimal vmp;

    @Enumerated(EnumType.STRING)
    @Column(name = "conformity", length = 20)
    private ConformityStatus conformity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    @Column(name = "analyzed_by")
    private UUID analyzedBy;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "instrument_reading", columnDefinition = "text")
    private String instrumentReading;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "quality_control", columnDefinition = "jsonb")
    private String qualityControl;

    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum ConformityStatus {
        CONFORMING, NON_CONFORMING, INCONCLUSIVE, NOT_APPLICABLE;
    }

    public enum AnalysisStatus {
        PENDING, IN_PROGRESS, COMPLETED, VALIDATED, PUBLISHED, REJECTED, REANALYSIS;
    }
}