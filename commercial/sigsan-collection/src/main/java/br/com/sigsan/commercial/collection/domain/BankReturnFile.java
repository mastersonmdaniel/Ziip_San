package br.com.sigsan.commercial.collection.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Arquivo de retorno bancário recebido (registro A, total do trailer, hash do conteúdo e o
 * resultado da validação). O conteúdo cru fica fora do banco; {@code storageUri} aponta para ele.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "bank_return_files")
@Getter
@Setter
public class BankReturnFile extends TenantAwareEntity {

    public enum Layout { FEBRABAN_150_COLLECTION }

    public enum Status { RECEIVED, ACCEPTED, REJECTED }

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout", nullable = false, length = 30)
    private Layout layout;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "nsa", nullable = false)
    private long nsa;

    @Column(name = "generated_on", nullable = false)
    private LocalDate generatedOn;

    @Column(name = "bank_code", nullable = false, length = 3)
    private String bankCode;

    @Column(name = "convenio_code", nullable = false, length = 20)
    private String convenioCode;

    @Column(name = "layout_version", nullable = false)
    private int layoutVersion;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "total_amount", nullable = false, precision = 17, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "storage_uri", length = 500)
    private String storageUri;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issues", columnDefinition = "jsonb")
    private String issues;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
