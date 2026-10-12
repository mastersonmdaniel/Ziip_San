package br.com.sigsan.grs.mtr.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waste_destination", indexes = {
        @Index(name = "idx_waste_destination_cnpj", columnList = "cnpj"),
        @Index(name = "idx_waste_destination_type", columnList = "destination_type"),
        @Index(name = "idx_waste_destination_status", columnList = "status")
})
@Getter
@Setter
public class WasteDestination extends TenantAwareEntity {

    @Column(name = "cnpj", length = 18, nullable = false, unique = true)
    private String cnpj;

    @Column(name = "corporate_name", nullable = false, length = 200)
    private String corporateName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, length = 30)
    private DestinationType destinationType; // LANDFILL, INCINERATION, RECYCLING, COMPOSTING, COPROCESSING, TRANSFER_STATION

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "environmental_license", columnDefinition = "jsonb")
    private String environmentalLicense;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "operational_license", columnDefinition = "jsonb")
    private String operationalLicense;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "accepted_waste_classes", columnDefinition = "jsonb")
    private String acceptedWasteClasses; // Array of waste class codes

    @Column(name = "capacity_daily_tons", precision = 10, scale = 3)
    private java.math.BigDecimal capacityDailyTons;

    @Column(name = "capacity_total_tons", precision = 12, scale = 3)
    private java.math.BigDecimal capacityTotalTons;

    @Column(name = "current_volume_tons", precision = 12, scale = 3)
    private java.math.BigDecimal currentVolumeTons;

    @Column(name = "coordinates", columnDefinition = "geometry(Point, 4674)")
    private org.locationtech.jts.geom.Point coordinates;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "monitoring_data", columnDefinition = "jsonb")
    private String monitoringData; // Leachate, biogas, groundwater quality

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum DestinationType {
        LANDFILL, // Aterro sanitário
        INCINERATION, // Incineração
        RECYCLING, // Reciclagem
        COMPOSTING, // Compostagem
        COPROCESSING, // Coprocessamento (fornos cimento)
        TRANSFER_STATION, // Estação de transbordo
        TREATMENT_PLANT, // Estação de tratamento
        REUSE; // Reuso/Recuperação energética
    }
}