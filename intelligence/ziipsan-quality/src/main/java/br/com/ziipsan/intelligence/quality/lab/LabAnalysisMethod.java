package br.com.ziipsan.intelligence.quality.lab;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lab_analysis_method", indexes = {
        @Index(name = "idx_lab_method_code", columnList = "code"),
        @Index(name = "idx_lab_method_param", columnList = "parameter_id"),
        @Index(name = "idx_lab_method_inst", columnList = "instrument_id")
})
@Getter
@Setter
public class LabAnalysisMethod extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id")
    private br.com.ziipsan.intelligence.quality.domain.QualityParameter parameter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id")
    private LabInstrument instrument;

    @Column(name = "standard_method", length = 100)
    private String standardMethod;

    @Column(name = "method_version", length = 20)
    private String methodVersion;

    @Column(name = "sample_preparation", columnDefinition = "text")
    private String samplePreparation;

    @Column(name = "procedure", columnDefinition = "text")
    private String procedure;

    @Column(name = "calculation_formula", columnDefinition = "text")
    private String calculationFormula;

    @Column(name = "reportable_limit", precision = 12, scale = 6)
    private BigDecimal reportableLimit;

    @Column(name = "detection_limit", precision = 12, scale = 6)
    private BigDecimal detectionLimit;

    @Column(name = "quantification_limit", precision = 12, scale = 6)
    private BigDecimal quantificationLimit;

    @Column(name = "precision_rsd", precision = 5, scale = 2)
    private BigDecimal precisionRsd;

    @Column(name = "accuracy_recovery", precision = 5, scale = 2)
    private BigDecimal accuracyRecovery;

    @Column(name = "measurement_uncertainty", precision = 8, scale = 4)
    private BigDecimal measurementUncertainty;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "reagents", columnDefinition = "jsonb")
    private String reagents;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "equipment_setup", columnDefinition = "jsonb")
    private String equipmentSetup;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "quality_control", columnDefinition = "jsonb")
    private String qualityControl;

    @Column(name = "safety_notes", columnDefinition = "text")
    private String safetyNotes;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;
}