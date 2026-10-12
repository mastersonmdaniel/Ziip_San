package br.com.sigsan.operations.metering.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Hidrômetro (o equipamento). Onde ele está instalado é {@link MeterInstallation}.
 *
 * <p>{@code digits} é o número de dígitos do mostrador: define a virada (10^digits) e o limite da
 * leitura usados pela cascata de consumo.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "meters")
@Getter
@Setter
public class Meter extends TenantAwareEntity {

    @Column(name = "meter_number", nullable = false, length = 30)
    private String meterNumber;

    @Column(name = "brand", length = 60)
    private String brand;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "metrological_class", length = 10)
    private String metrologicalClass;

    @Column(name = "nominal_diameter_mm", precision = 6, scale = 1)
    private BigDecimal nominalDiameterMm;

    @Column(name = "digits", nullable = false)
    private int digits;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Column(name = "inmetro_approval", length = 60)
    private String inmetroApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "last_revision_on")
    private LocalDate lastRevisionOn;

    @Column(name = "notes", length = 500)
    private String notes;

    public enum Status { AVAILABLE, INSTALLED, IN_MAINTENANCE, STOLEN, WRITTEN_OFF }

}
