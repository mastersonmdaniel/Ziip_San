package br.com.sigsan.grs.mtr.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waste_cdf", indexes = {
        @Index(name = "idx_waste_cdf_mtr", columnList = "mtr_id", unique = true),
        @Index(name = "idx_waste_cdf_destination", columnList = "destination_id"),
        @Index(name = "idx_waste_cdf_issued", columnList = "issued_at")
})
@Getter
@Setter
public class WasteCdf extends TenantAwareEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mtr_id", nullable = false, unique = true)
    private WasteMtr mtr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private WasteDestination destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_method", nullable = false, length = 30)
    private DestinationMethod destinationMethod;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "process_details", columnDefinition = "jsonb")
    private String processDetails; // {temperature, pressure, duration, efficiency, byproducts}

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "technician_name", length = 200)
    private String technicianName;

    @Column(name = "technician_crea", length = 30)
    private String technicianCrea;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "pdf_document", columnDefinition = "bytea")
    private byte[] pdfDocument;

    @Column(name = "pdf_checksum", length = 64)
    private String pdfChecksum; // SHA-256

    @Column(name = "hash_chain", length = 128)
    private String hashChain;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum DestinationMethod {
        LANDFILL, // Disposição em aterro sanitário
        INCINERATION, // Incineração
        RECYCLING, // Reciclagem
        COMPOSTING, // Compostagem
        COPROCESSING, // Coprocessamento
        TREATMENT, // Tratamento físico-químico/biológico
        REUSE, // Reuso direto
        ENERGY_RECOVERY; // Recuperação energética
    }
}