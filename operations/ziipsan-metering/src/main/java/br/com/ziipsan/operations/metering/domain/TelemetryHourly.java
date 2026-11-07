package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.telemetry.DataState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma hora da curva horária (ED-17): o volume de uma hora de um canal de um
 * ponto. Tabela particionada por mês ({@code bucketMonth}, o mês de
 * {@code hourStart}) pelo padrão da P2.3, sem auditoria por linha, com
 * índice por ponto e tempo — a consulta da curva é sempre por ponto e
 * intervalo.
 *
 * <p>Dado pessoal por minimização: a linha guarda só ponto, canal, unidade,
 * hora, volumes e estado — nada do titular. A retenção curta é a de
 * {@code ami.retention_days} e os agregados vivem na tabela de resumo.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "telemetry_hourly_profile")
@Getter
@Setter
public class TelemetryHourly extends TenantAwareEntity {

    @Column(name = "measuring_point_id", nullable = false)
    private UUID measuringPointId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "bucket_month", nullable = false)
    private LocalDate bucketMonth;

    @Column(name = "hour_start", nullable = false)
    private OffsetDateTime hourStart;

    @Column(name = "volume_litres", nullable = false, precision = 14, scale = 3)
    private BigDecimal volumeLitres;

    @Column(name = "reverse_volume_litres", precision = 14, scale = 3)
    private BigDecimal reverseVolumeLitres;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_state", nullable = false, length = 15)
    private DataState dataState;
}
