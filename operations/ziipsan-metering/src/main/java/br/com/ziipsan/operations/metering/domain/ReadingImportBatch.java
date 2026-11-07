package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Lote de importacao do arquivo de volta do fornecedor (P2.7, V5_3): um por
 * arquivo recebido. {@code fileSha256} e o hash do conteudo exato e tem UNIQUE
 * por tenant -- reenviar o mesmo arquivo devolve este lote, sem reprocessar
 * (idempotencia de arquivo do aceite). {@code externalRef} e a referencia do
 * lote no fornecedor, quando ele a informa. Os contadores fecham com as
 * linhas de {@code reading_import_records} do lote.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "reading_import_batches")
@Getter
@Setter
public class ReadingImportBatch extends TenantAwareEntity {

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "layout_code", nullable = false, length = 30)
    private String layoutCode;

    @Column(name = "reference_month")
    private LocalDate referenceMonth;

    @Column(name = "route_code", length = 40)
    private String routeCode;

    @Column(name = "external_ref", length = 60)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private Status status = Status.RECEIVED;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "imported_records", nullable = false)
    private int importedRecords;

    @Column(name = "duplicate_records", nullable = false)
    private int duplicateRecords;

    @Column(name = "error_records", nullable = false)
    private int errorRecords;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    /** RECEIVED ao gravar o lote; PROCESSED ao fechar os contadores; REJECTED quando o cabecalho nao e o do leiaute. */
    public enum Status { RECEIVED, PROCESSED, REJECTED }

    public void setReferenceMonth(YearMonth month) {
        this.referenceMonth = month == null ? null : month.atDay(1);
    }

    public YearMonth referenceMonth() {
        return referenceMonth == null ? null : YearMonth.from(referenceMonth);
    }
}
