package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.telemetry.DataState;
import br.com.ziipsan.operations.metering.telemetry.VeeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Leitura AMR bruta de telemetria (ED-17), ANTES da VEE: a linha que chegou
 * por REST ou por arquivo. Vive em tabela particionada por mês
 * ({@code referenceMonth}, o mês de {@code readAt}) pelo padrão da P2.3 e
 * <b>sem auditoria por linha</b> — é série de alto volume; a trilha é a da
 * VEE ({@code veeStatus} e {@code veeDetail}) e a da promoção.
 *
 * <p>Só vira leitura de faturamento pela promoção da VEE, que grava
 * {@code promotedReadingId} (e o mês dela em {@code promotedReference}, o
 * mesmo desnormalizado da V5_4) e marca {@code veeStatus = PROMOTED}.
 *
 * <p>{@code idempotencyKey} é a referência externa da leitura: única por
 * (tenant, mês, chave), o reenvio não duplica.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "telemetry_readings")
@Getter
@Setter
public class TelemetryReading extends TenantAwareEntity {

    @Column(name = "measuring_point_id", nullable = false)
    private UUID measuringPointId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    /** Leitura do mostrador; nula enquanto a coleta não entregou leitura (a VEE estima ou rejeita). */
    @Column(name = "dial_reading")
    private Integer dialReading;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_state", nullable = false, length = 15)
    private DataState dataState;

    @Enumerated(EnumType.STRING)
    @Column(name = "vee_status", nullable = false, length = 15)
    private VeeStatus veeStatus;

    /** Detalhe da VEE em JSON (violações, estimativa, edição), pela classe {@code Json} do core. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vee_detail", columnDefinition = "jsonb")
    private String veeDetail;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "promoted_reading_id")
    private UUID promotedReadingId;

    @Column(name = "promoted_reference")
    private LocalDate promotedReference;
}
