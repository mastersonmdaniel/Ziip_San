package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Cobranca Pix (P3.2, V6_3). {@code dueDate} nulo = Pix sem vencimento (nao pode
 * compor cobranca hibrida, que exige Pix Cobranca COM vencimento).
 * {@code collectionMonth}/{@code fiscalDocumentId} sao a referencia de
 * arrecadacao e o vinculo fiscal da P3.1 (DAD-19).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_charges")
@Getter
@Setter
public class PixChargeEntity extends TenantAwareEntity {

    @Column(name = "psp", nullable = false, length = 40)
    private String psp;

    @Column(name = "txid", nullable = false, length = 35)
    private String txid;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** ATIVA, PAGA, CANCELADA, EXPIRADA, REMOVIDA_PELO_PSP. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ATIVA";

    @Column(name = "end_to_end_id", length = 32)
    private String endToEndId;

    @Column(name = "hybrid_document_id")
    private UUID hybridDocumentId;

    @Column(name = "collection_month", nullable = false)
    private LocalDate collectionMonth;

    @Column(name = "fiscal_document_id")
    private UUID fiscalDocumentId;

    @Column(name = "payer_document", length = 14)
    private String payerDocument;
}
