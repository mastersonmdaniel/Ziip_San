package br.com.sigsan.intelligence.losses.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "water_balance", indexes = {
        @Index(name = "idx_wb_period", columnList = "period_start, period_end"),
        @Index(name = "idx_wb_dma", columnList = "dma_id"),
        @Index(name = "idx_wb_status", columnList = "status")
})
@Getter
@Setter
public class WaterBalance extends TenantAwareEntity {

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "frequency", length = 20)
    private String frequency = "MONTHLY";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dma_id")
    private DmaDistrict dmaDistrict;

    @Column(name = "system_input_volume", precision = 14, scale = 3)
    private BigDecimal systemInputVolume;

    @Column(name = "authorized_consumption", precision = 14, scale = 3)
    private BigDecimal authorizedConsumption;

    @Column(name = "billed_authorized", precision = 14, scale = 3)
    private BigDecimal billedAuthorized;

    @Column(name = "unbilled_authorized", precision = 14, scale = 3)
    private BigDecimal unbilledAuthorized;

    @Column(name = "apparent_losses", precision = 14, scale = 3)
    private BigDecimal apparentLosses;

    @Column(name = "unauthorized_consumption", precision = 14, scale = 3)
    private BigDecimal unauthorizedConsumption;

    @Column(name = "metering_inaccuracies", precision = 14, scale = 3)
    private BigDecimal meteringInaccuracies;

    @Column(name = "real_losses", precision = 14, scale = 3)
    private BigDecimal realLosses;

    @Column(name = "leakage_transmission", precision = 14, scale = 3)
    private BigDecimal leakageTransmission;

    @Column(name = "leakage_storage", precision = 14, scale = 3)
    private BigDecimal leakageStorage;

    @Column(name = "leakage_service_connections", precision = 14, scale = 3)
    private BigDecimal leakageServiceConnections;

    @Column(name = "non_revenue_water", precision = 14, scale = 3)
    private BigDecimal nonRevenueWater;

    @Column(name = "nrw_percentage", precision = 8, scale = 4)
    private BigDecimal nrwPercentage;

    @Column(name = "infrastructure_leakage_index", precision = 8, scale=4)
    private BigDecimal infrastructureLeakageIndex;

    @Column(name = "unavoidable_annual_real_losses", precision = 14, scale = 3)
    private BigDecimal unavoidableAnnualRealLosses;

    @Column(name = "data_quality_grade", length = 20)
    private String dataQualityGrade;
    @Column(name = "status", length = 20)
    private String status = "DRAFT";

    @Column(name = "calculated_at")
    private OffsetDateTime calculatedAt;

    @Column(name = "calculated_by")
    private UUID calculatedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;
}