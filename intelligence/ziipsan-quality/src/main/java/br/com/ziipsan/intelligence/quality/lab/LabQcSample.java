package br.com.ziipsan.intelligence.quality.lab;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_qc_sample", indexes = {
        @Index(name = "idx_qc_sample_method", columnList = "method_id"),
        @Index(name = "idx_qc_sample_type", columnList = "qc_type"),
        @Index(name = "idx_qc_sample_analyzed", columnList = "analyzed_at"),
        @Index(name = "idx_qc_sample_status", columnList = "status")
})
@Getter
@Setter
public class LabQcSample extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "method_id", nullable = false)
    private LabAnalysisMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "qc_type", length = 30)
    private QcType qcType;

    @Column(name = "sample_id", length = 50)
    private String sampleId;

    @Column(name = "expected_value", precision = 12, scale = 6)
    private BigDecimal expectedValue;

    @Column(name = "measured_value", precision = 12, scale = 6)
    private BigDecimal measuredValue;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "deviation", precision = 8, scale = 4)
    private BigDecimal deviation;

    @Column(name = "deviation_percent", precision = 8, scale = 4)
    private BigDecimal deviationPercent;

    @Column(name = "control_limit_upper", precision = 12, scale = 6)
    private BigDecimal controlLimitUpper;

    @Column(name = "control_limit_lower", precision = 12, scale = 6)
    private BigDecimal controlLimitLower;

    @Column(name = "warning_limit_upper", precision = 12, scale = 6)
    private BigDecimal warningLimitUpper;

    @Column(name = "warning_limit_lower", precision = 12, scale = 6)
    private BigDecimal warningLimitLower;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private QcStatus status = QcStatus.PENDING;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    @Column(name = "analyzed_by")
    private UUID analyzedBy;

    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "run_id", length = 50)
    private String runId;

    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum QcType {
        BLANK, STANDARD, SPIKE, DUPLICATE, CRM, PT_SAMPLE, BLIND;
    }

    public enum QcStatus {
        PENDING, PASSED, WARNING, FAILED, INVESTIGATING, REJECTED;
    }
}