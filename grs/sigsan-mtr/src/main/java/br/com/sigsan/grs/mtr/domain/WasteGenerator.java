package br.com.sigsan.grs.mtr.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waste_generator", indexes = {
        @Index(name = "idx_waste_generator_cnpj", columnList = "cnpj_cpf"),
        @Index(name = "idx_waste_generator_status", columnList = "status"),
        @Index(name = "idx_waste_generator_cadri", columnList = "cadri_number")
})
@Getter
@Setter
public class WasteGenerator extends TenantAwareEntity {

    @Column(name = "cnpj_cpf", length = 18, nullable = false)
    private String cnpjCpf;

    @Column(name = "corporate_name", nullable = false, length = 200)
    private String corporateName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "address", columnDefinition = "jsonb")
    private String address;

    @Column(name = "cnae_code", length = 7)
    private String cnaeCode;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "environmental_license", columnDefinition = "jsonb")
    private String environmentalLicense; // {number, issuingBody, issueDate, expiryDate, type}

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "responsible_technician", columnDefinition = "jsonb")
    private String responsibleTechnician; // {name, crea, art, contact}

    @Column(name = "cadri_number", length = 50)
    private String cadriNumber;

    @Column(name = "cadri_validity")
    private LocalDate cadriValidity;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;
}