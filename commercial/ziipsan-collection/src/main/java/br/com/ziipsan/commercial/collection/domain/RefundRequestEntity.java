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
 * Devolucao de pagamento por OS REFUND_REQUEST (P3.29, V6_5), ligada ao
 * pagamento de origem. Sem origem ou com valor divergente, {@code status} =
 * PENDENTE e {@code pendingReason} diz o motivo - a devolucao nao executa.
 * Na pendencia, {@code baseAmount}/{@code refundAmount} sao NULOS (nada foi
 * calculado; sem origem pode nem haver base): as colunas sao anulaveis e o
 * CHECK da V6_5 impoe valor obrigatorio e &gt; 0 fora de pendencia.
 * O destino bancario vive SOMENTE em {@code destinationCiphertext}
 * (FieldCipher, contexto refunds.destination), com retencao em
 * {@code destinationRetentionUntil}; nenhum campo em claro guarda destino.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "refund_requests")
@Getter
@Setter
public class RefundRequestEntity extends TenantAwareEntity {

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    @Column(name = "origin_payment_id")
    private UUID originPaymentId;

    /** DUPLICATE_PAYMENT ou UNDUE_CHARGE. */
    @Column(name = "refund_type", nullable = false, length = 24)
    private String refundType;

    /**
     * P3_1_CLASSIFICATION, P3_2_HYBRID_SECOND_PAYMENT ou MANUAL_REQUEST.
     * Largura 40 (V6_5 pos-revisao): o literal mais longo tem 26 caracteres.
     */
    @Column(name = "source", nullable = false, length = 40)
    private String source;

    /** Anulavel: nulo enquanto PENDENTE (ver CHECK da V6_5). */
    @Column(name = "base_amount", precision = 14, scale = 2)
    private BigDecimal baseAmount;

    /** Anulavel: nulo enquanto PENDENTE (ver CHECK da V6_5). */
    @Column(name = "refund_amount", precision = 14, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "double_applied", nullable = false)
    private boolean doubleApplied;

    @Column(name = "double_waiver_justification", length = 500)
    private String doubleWaiverJustification;

    @Column(name = "double_waiver_norm_citation", length = 300)
    private String doubleWaiverNormCitation;

    /** USER_CHOICE, CREDIT_NEXT_BILL ou BANK_TRANSFER (pay.refund_mode). */
    @Column(name = "refund_mode", nullable = false, length = 20)
    private String refundMode;

    @Column(name = "chosen_mode", length = 20)
    private String chosenMode;

    /** PIX_RETURN_TO_PAYER, PIX_KEY ou BANK_ACCOUNT (pay.refund_destination). */
    @Column(name = "refund_destination", length = 24)
    private String refundDestination;

    /** SOMENTE texto cifrado do FieldCipher; nunca o destino em claro. */
    @Column(name = "destination_ciphertext", columnDefinition = "TEXT")
    private String destinationCiphertext;

    @Column(name = "destination_retention_until")
    private LocalDate destinationRetentionUntil;

    /** PENDENTE, AGUARDANDO_APROVACAO, APROVADA, ENCERRADA ou RECUSADA. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDENTE";

    /** MISSING_ORIGIN_PAYMENT ou AMOUNT_DIVERGENCE (obrigatorio se PENDENTE). */
    @Column(name = "pending_reason", length = 28)
    private String pendingReason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "first_approver_id")
    private UUID firstApproverId;

    @Column(name = "second_approver_id")
    private UUID secondApproverId;
}
