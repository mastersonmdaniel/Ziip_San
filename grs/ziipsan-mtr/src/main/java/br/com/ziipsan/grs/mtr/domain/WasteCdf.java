package br.com.ziipsan.grs.mtr.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E2.
// O CDF oficial agrega VÁRIOS MTRs de um período (Portaria MMA 280/2020, art. 14 §§3º–7º, via
// levantamento regulatório): o modelo anterior era 1:1 com o MTR (waste_cdf.mtr_id UNIQUE), o que
// está errado. Este CDF é o registo INTERNO do destinador: agrega os MTRs destinados no período
// (ligação em waste_cdf_mtr, entidade WasteCdfMtr) e NÃO se apresenta como CDF oficial — o oficial
// é o emitido pelo sistema emissor (SINIR/IMA); official_cdf_number só é gravado quando a
// integração real o devolver, nunca fabricado.
@Entity
@Table(name = "waste_cdf", indexes = {
        @Index(name = "idx_waste_cdf_destination", columnList = "destination_id"),
        @Index(name = "idx_waste_cdf_issued", columnList = "issued_at"),
        @Index(name = "idx_waste_cdf_period", columnList = "period_start, period_end")
})
@Getter
@Setter
public class WasteCdf extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private WasteDestination destination;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** Número do CDF oficial no sistema emissor; nulo enquanto não houver integração real. */
    @Column(name = "official_cdf_number", length = 50)
    private String officialCdfNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_method", nullable = false, length = 30)
    private DestinationMethod destinationMethod;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "process_details", columnDefinition = "jsonb")
    private String processDetails; // {temperature, pressure, duration, efficiency, byproducts}

    @Column(name = "technician_id")
    private java.util.UUID technicianId;

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
