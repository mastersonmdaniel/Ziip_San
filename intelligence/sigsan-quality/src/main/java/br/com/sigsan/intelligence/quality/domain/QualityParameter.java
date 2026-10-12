package br.com.sigsan.intelligence.quality.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quality_parameter", indexes = {
        @Index(name = "idx_quality_param_code", columnList = "code"),
        @Index(name = "idx_quality_param_category", columnList = "category"),
        @Index(name = "idx_quality_param_portaria", columnList = "portaria_ms_888")
})
@Getter
@Setter
public class QualityParameter extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "unit", length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private ParameterCategory category;

    @Column(name = "portaria_ms_888")
    private Boolean portariaMs888 = false;

    @Column(name = "vmp", precision = 10, scale = 4)
    private BigDecimal vmp;

    @Column(name = "alert_min", precision = 10, scale = 4)
    private BigDecimal alertMin;

    @Column(name = "alert_max", precision = 10, scale = 4)
    private BigDecimal alertMax;

    @Column(name = "critical_min", precision = 10, scale = 4)
    private BigDecimal criticalMin;

    @Column(name = "critical_max", precision = 10, scale = 4)
    private BigDecimal criticalMax;

    @Column(name = "method_code", length = 50)
    private String methodCode;

    @Column(name = "method_description", columnDefinition = "text")
    private String methodDescription;

    @Column(name = "sampling_frequency", length = 50)
    private String samplingFrequency;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum ParameterCategory {
        PHYSICAL, CHEMICAL, MICROBIOLOGICAL, RADIOLOGICAL, ORGANOLEPTIC;
    }
}