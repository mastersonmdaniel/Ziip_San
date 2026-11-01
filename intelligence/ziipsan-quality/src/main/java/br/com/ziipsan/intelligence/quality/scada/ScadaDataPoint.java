package br.com.ziipsan.intelligence.quality.scada;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "scada_data_point", indexes = {
        @Index(name = "idx_scada_data_tag_time", columnList = "tag_id, timestamp"),
        @Index(name = "idx_scada_data_time", columnList = "timestamp"),
        @Index(name = "idx_scada_data_quality", columnList = "quality")
})
@Getter
@Setter
public class ScadaDataPoint extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private ScadaTag tag;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "value", precision = 18, scale = 6)
    private BigDecimal value;

    @Column(name = "raw_value")
    private BigDecimal rawValue;

    @Column(name = "quality")
    private Integer quality = 192;

    @Column(name = "flags", length = 50)
    private String flags;

    @Column(name = "interpolated")
    private Boolean interpolated = false;

    @Column(name = "annotation", columnDefinition = "text")
    private String annotation;
}