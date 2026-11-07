package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O agregado diário da curva horária (ED-17): uma linha por ponto, canal e
 * dia, produzida pelo {@code TelemetryRollupService} conforme
 * {@code tele.rollup_policy}. É o que sobrevive ao descarte da série
 * horária pela retenção — o resumo fica, a hora a hora sai.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "telemetry_hourly_daily_summary")
@Getter
@Setter
public class TelemetryHourlyDailySummary extends TenantAwareEntity {

    @Column(name = "measuring_point_id", nullable = false)
    private UUID measuringPointId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "total_volume_litres", nullable = false, precision = 16, scale = 3)
    private BigDecimal totalVolumeLitres;

    @Column(name = "total_reverse_litres", nullable = false, precision = 16, scale = 3)
    private BigDecimal totalReverseLitres;

    @Column(name = "samples", nullable = false)
    private int samples;
}
