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
 * Documento hibrido (P3.2, V6_3; Res. BCB 587/2026): o mesmo documento cobrado
 * por Pix Cobranca com vencimento e por boleto, com BAIXA UNICA. O emissor do
 * boleto ({@code slipIssuerPsp}) e sempre o PSP do recebedor.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "hybrid_documents")
@Getter
@Setter
public class HybridDocumentEntity extends TenantAwareEntity {

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "pix_charge_id", nullable = false)
    private UUID pixChargeId;

    @Column(name = "slip_issuer_psp", nullable = false, length = 40)
    private String slipIssuerPsp;

    /** COMUM ou DINAMICO (nunca vinculado a ativo financeiro). */
    @Column(name = "slip_kind", nullable = false, length = 16)
    private String slipKind;

    @Column(name = "slip_barcode", length = 44)
    private String slipBarcode;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** ABERTO, BAIXADO_PIX, BAIXADO_BOLETO, CANCELADO. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "ABERTO";

    @Column(name = "settled_payment_id")
    private UUID settledPaymentId;

    @Column(name = "collection_month", nullable = false)
    private LocalDate collectionMonth;

    @Column(name = "fiscal_document_id")
    private UUID fiscalDocumentId;
}
