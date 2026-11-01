package br.com.ziipsan.grs.mtr.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waste_generator", indexes = {
        @Index(name = "idx_waste_generator_document", columnList = "tenant_id, document_hash", unique = true),
        @Index(name = "idx_waste_generator_status", columnList = "status"),
        @Index(name = "idx_waste_generator_cadri", columnList = "cadri_number")
})
@Getter
@Setter
public class WasteGenerator extends TenantAwareEntity {

    // CPF (gerador pessoa física) ou CNPJ: nunca em claro (V2_8). A unicidade por tenant é pelo
    // índice cego (document_hash); gravado só por MtrDocumentProtection.
    @Column(name = "document_ciphertext", columnDefinition = "text", nullable = false)
    private String documentCiphertext;

    @Column(name = "document_hash", length = 64, nullable = false)
    private String documentHash;

    @Column(name = "document_masked", length = 30)
    private String documentMasked;

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