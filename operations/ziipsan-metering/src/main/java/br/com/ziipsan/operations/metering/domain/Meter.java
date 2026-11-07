package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
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
public class Meter extends TenantAwareEntity {

    @Column(name = "meter_number", nullable = false, length = 30)
    @Setter private String meterNumber;

    @Column(name = "brand", length = 60)
    @Setter private String brand;

    @Column(name = "model", length = 60)
    @Setter private String model;

    @Column(name = "metrological_class", length = 10)
    @Setter private String metrologicalClass;

    @Column(name = "nominal_diameter_mm", precision = 6, scale = 1)
    @Setter private BigDecimal nominalDiameterMm;

    @Column(name = "digits", nullable = false)
    @Setter private int digits;

    @Column(name = "manufacture_year")
    @Setter private Integer manufactureYear;

    @Column(name = "inmetro_approval", length = 60)
    @Setter private String inmetroApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "last_revision_on")
    @Setter private LocalDate lastRevisionOn;

    // --- ED-14 (hidrometria, Portaria 155/2022): catalogo substitui texto livre;
    // classe antiga A-D migrada fica em legacyMetrologicalClass com migratedPark;
    // lacre NAO mora aqui (lugar unico: meter_installations.seal_number). ---
    @Column(name = "model_id")
    @Setter private java.util.UUID modelId;

    @Column(name = "lot_id")
    @Setter private java.util.UUID lotId;

    /** Ativo da P1.35 (V27_1), classe propria HIDROMETRO; FK no SQL pendente da ED-14. */
    @Column(name = "asset_id")
    @Setter private java.util.UUID assetId;

    @Column(name = "storage_location_id")
    @Setter private java.util.UUID storageLocationId;

    @Column(name = "legacy_metrological_class", length = 10)
    @Setter private String legacyMetrologicalClass;

    @Column(name = "migrated_park", nullable = false)
    @Setter private boolean migratedPark;

    @Column(name = "write_off_reason", length = 300)
    @Setter private String writeOffReason;

    @Column(name = "totalized_volume_m3", nullable = false)
    @Setter private long totalizedVolumeM3;

    @Column(name = "notes", length = 500)
    @Setter private String notes;

    public enum Status { AVAILABLE, INSTALLED, IN_MAINTENANCE, STOLEN, WRITTEN_OFF }

    /**
     * ÚNICA escrita de estado (correção A4): NÃO há setter público de {@code status}.
     * Quem chama é o MeterLifecycleService, depois de validar a transição em
     * MeterLifecycleRule (instalação, retirada ou movimentação com OS). O teste
     * MeterLifecycleTest trava por reflexão a ausência do setter de status.
     */
    public void transitionTo(Status newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Novo estado do hidrômetro é obrigatório");
        this.status = newStatus;
    }

}
