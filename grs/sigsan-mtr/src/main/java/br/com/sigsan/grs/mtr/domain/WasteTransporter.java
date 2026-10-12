package br.com.sigsan.grs.mtr.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waste_transporter", indexes = {
        @Index(name = "idx_waste_transporter_cnpj", columnList = "cnpj"),
        @Index(name = "idx_waste_transporter_rntrc", columnList = "rntrc"),
        @Index(name = "idx_waste_transporter_status", columnList = "status")
})
@Getter
@Setter
public class WasteTransporter extends TenantAwareEntity {

    @Column(name = "cnpj", length = 18, nullable = false, unique = true)
    private String cnpj;

    @Column(name = "corporate_name", nullable = false, length = 200)
    private String corporateName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @Column(name = "rntrc", length = 20, unique = true)
    private String rntrc; // Registro Nacional de Transportadores Rodoviários de Cargas

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "antt_license", columnDefinition = "jsonb")
    private String anttLicense; // {number, expiryDate, categories}

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "vehicles", columnDefinition = "jsonb")
    private String vehicles; // Array of vehicles: [{plate, capacity, type, licenseExpiry}]

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "drivers", columnDefinition = "jsonb")
    private String drivers; // Array of drivers: [{name, cpf, cnh, cnhExpiry, moppExpiry}]

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "insurance", columnDefinition = "jsonb")
    private String insurance; // {policyNumber, insurer, expiryDate, coverage}

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "operational_area", columnDefinition = "jsonb")
    private String operationalArea; // GeoJSON polygon or list of municipalities

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;
}