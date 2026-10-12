package br.com.sigsan.grs.mtr.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waste_mtr", indexes = {
        @Index(name = "idx_waste_mtr_number", columnList = "mtr_number", unique = true),
        @Index(name = "idx_waste_mtr_generator", columnList = "generator_id"),
        @Index(name = "idx_waste_mtr_transporter", columnList = "transporter_id"),
        @Index(name = "idx_waste_mtr_destination", columnList = "destination_id"),
        @Index(name = "idx_waste_mtr_status", columnList = "status"),
        @Index(name = "idx_waste_mtr_issued", columnList = "issued_at"),
        @Index(name = "idx_waste_mtr_collected", columnList = "collected_at"),
        @Index(name = "idx_waste_mtr_delivered", columnList = "delivered_at"),
        @Index(name = "idx_waste_mtr_destined", columnList = "destined_at"),
        @Index(name = "idx_waste_mtr_sinir", columnList = "sinir_sync_status")
})
@Getter
@Setter
public class WasteMtr extends TenantAwareEntity {

    @Column(name = "mtr_number", nullable = false, length = 50, unique = true)
    private String mtrNumber; // Padrão SINIR: AAAA.NNNNNNNN.AAAA.NN

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generator_id", nullable = false)
    private WasteGenerator generator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transporter_id", nullable = false)
    private WasteTransporter transporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private WasteDestination destination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waste_class_id", nullable = false)
    private WasteClass wasteClass;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "packaging_type", length = 50)
    private String packagingType; // IBC, BOMBONA, TAMBOR, BIG_BAG, A_GRANEL, etc.

    @Column(name = "packaging_count")
    private Integer packagingCount;

    @Column(name = "vehicle_plate", length = 10)
    private String vehiclePlate;

    @Column(name = "driver_cpf", length = 14)
    private String driverCpf;

    @Column(name = "route_geometry", columnDefinition = "geometry(LineString, 4674)")
    private org.locationtech.jts.geom.LineString routeGeometry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MtrStatus status = MtrStatus.EMITTED;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt; // At destination

    @Column(name = "destined_at")
    private OffsetDateTime destinedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    // Digital Signatures (ICP-Brasil)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "generator_signature", columnDefinition = "jsonb")
    private String generatorSignature; // {certificate, signedAt, hash, algorithm}

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "transporter_signature", columnDefinition = "jsonb")
    private String transporterSignature;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "destination_signature", columnDefinition = "jsonb")
    private String destinationSignature;

    // Hash chain for immutability
    @Column(name = "hash_chain", length = 128)
    private String hashChain; // SHA-256 chain

    @Column(name = "previous_mtr_id")
    private UUID previousMtrId; // For MTR chain (fracionamento, agrupamento)

    @Column(name = "is_complementary")
    private Boolean isComplementary = false; // MTR complementar

    @Column(name = "parent_mtr_id")
    private UUID parentMtrId; // MTR original se complementar

    // SINIR Integration
    @Enumerated(EnumType.STRING)
    @Column(name = "sinir_sync_status", length = 30)
    private SinirSyncStatus sinirSyncStatus = SinirSyncStatus.PENDING;

    @Column(name = "sinir_sync_at")
    private OffsetDateTime sinirSyncAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "sinir_response", columnDefinition = "jsonb")
    private String sinirResponse;

    @Column(name = "sinir_protocol", length = 50)
    private String sinirProtocol;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties;

    public enum MtrStatus {
        EMITTED, // Emitido pelo gerador
        COLLECTED, // Coletado pelo transportador
        IN_TRANSIT, // Em trânsito
        RECEIVED, // Recebido no destinador
        DESTINED, // Destinado (processado/finalizado)
        CANCELLED, // Cancelado
        REJECTED, // Rejeitado pelo destinador
        EXPIRED; // Expirado sem coleta
    }

    public enum SinirSyncStatus {
        PENDING,
        SYNCING,
        SYNCED,
        FAILED,
        RETRYING;
    }
}