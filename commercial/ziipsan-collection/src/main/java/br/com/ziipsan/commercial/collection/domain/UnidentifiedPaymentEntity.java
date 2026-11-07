package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Entrada da fila de pagamentos nao identificados (P3.29, V6_5).
 * SEM cliente ficticio: nao ha coluna de titular; unidade e documento
 * nascem nulos e so a OS PAYMENT_IDENTIFICATION os preenche.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unidentified_payments")
@Getter
@Setter
public class UnidentifiedPaymentEntity extends TenantAwareEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    /** UNIDENTIFIED_CLASSIFICATION ou PIX_WITHOUT_TXID. */
    @Column(name = "queue_reason", nullable = false, length = 28)
    private String queueReason;

    /**
     * NA_FILA, EM_IDENTIFICACAO, IDENTIFICADO ou DEVOLVIDO_CLASSIFICACAO.
     * Largura 40 (V6_5 pos-revisao): o literal mais longo tem 23 caracteres.
     */
    @Column(name = "status", nullable = false, length = 40)
    private String status = "NA_FILA";

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "identification_service_order_id")
    private UUID identificationServiceOrderId;

    @Column(name = "identified_by")
    private UUID identifiedBy;

    @Column(name = "identified_at")
    private Instant identifiedAt;
}
