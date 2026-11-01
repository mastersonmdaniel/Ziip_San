package br.com.ziipsan.intelligence.quality.scada;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scada_tag", indexes = {
        @Index(name = "idx_scada_tag_name", columnList = "tag_name"),
        @Index(name = "idx_scada_tag_source", columnList = "source_system"),
        @Index(name = "idx_scada_tag_point", columnList = "sampling_point_id")
})
@Getter
@Setter
public class ScadaTag extends TenantAwareEntity {

    @Column(name = "tag_name", nullable = false, length = 100, unique = true)
    private String tagName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "source_tag", length = 100)
    private String sourceTag;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "scale_factor", precision = 10, scale = 6)
    private BigDecimal scaleFactor = BigDecimal.ONE;

    @Column(name = "offset_value", precision = 10, scale = 6)
    private BigDecimal offsetValue = BigDecimal.ZERO;

    @Column(name = "min_raw")
    private BigDecimal minRaw;

    @Column(name = "max_raw")
    private BigDecimal maxRaw;

    @Column(name = "min_engineering")
    private BigDecimal minEngineering;

    @Column(name = "max_engineering")
    private BigDecimal maxEngineering;

    @Column(name = "deadband", precision = 10, scale = 6)
    private BigDecimal deadband;

    @Column(name = "scan_rate_ms")
    private Integer scanRateMs = 1000;

    @Column(name = "compression_enabled")
    private Boolean compressionEnabled = true;

    @Column(name = "compression_deviation", precision = 5, scale = 2)
    private BigDecimal compressionDeviation = BigDecimal.valueOf(0.01);

    @Column(name = "sampling_point_id")
    private UUID samplingPointId;

    @Column(name = "quality_parameter_id")
    private UUID qualityParameterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", length = 20)
    private DataType dataType = DataType.FLOAT;
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "last_value")
    private BigDecimal lastValue;

    @Column(name = "last_quality")
    private Integer lastQuality = 192;

    @Column(name = "last_timestamp")
    private OffsetDateTime lastTimestamp;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum DataType {
        BOOL, INT, FLOAT, DOUBLE, STRING;
    }
}