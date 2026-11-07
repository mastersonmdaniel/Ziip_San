package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dispositivo de telemetria de um ponto de medição (ED-17): o equipamento
 * (concentrador, módulo de rádio, datalogger) que coleta e transmite.
 *
 * <p>{@code clockDriftSeconds} é o desvio do relógio do dispositivo na
 * última comunicação, em segundos (positivo = adiantado): o relógio e o fuso
 * fazem parte do modelo de medição porque leitura com relógio errado cai na
 * hora errada da curva e no ciclo errado.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "telemetry_devices")
@Getter
@Setter
public class TelemetryDevice extends TenantAwareEntity {

    @Column(name = "measuring_point_id", nullable = false)
    private UUID measuringPointId;

    @Column(name = "serial_number", nullable = false, length = 60)
    private String serialNumber;

    @Column(name = "vendor", length = 60)
    private String vendor;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "clock_drift_seconds")
    private Integer clockDriftSeconds;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "active", nullable = false)
    private boolean active;
}
