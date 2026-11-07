package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Canal de um dispositivo de telemetria (ED-17): a grandeza que o
 * dispositivo transmite naquele canal (o modelo de medição é ponto →
 * dispositivo → canal). O {@code code} é o código do canal no dispositivo e
 * nos arquivos AMR; {@code unitOfMeasure} é o texto da grandeza como o
 * tenant a cadastra (ex.: litros) — sem catálogo inventado neste código.
 *
 * <p>Não usar {@code @Builder}.
 */
@Entity
@Table(name = "telemetry_channels")
@Getter
@Setter
public class TelemetryChannel extends TenantAwareEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "measuring_point_id", nullable = false)
    private UUID measuringPointId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "description", length = 120)
    private String description;

    @Column(name = "unit_of_measure", length = 20)
    private String unitOfMeasure;

    /** Dígitos do mostrador a que o canal se refere; a VEE limita a leitura por ele. Nulo = canal sem mostrador. */
    @Column(name = "dial_digits")
    private Integer dialDigits;

    @Column(name = "active", nullable = false)
    private boolean active;
}
