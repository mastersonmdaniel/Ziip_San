package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Classificacao de um pagamento (P3.1): a decisao que grava o efeito da baixa.
 * Classificacao MANUAL so produz efeito depois da APROVACAO DUPLA: dois aprovadores
 * distintos, nenhum deles o solicitante ({@code requestedBy}). A segregacao segue a
 * regra da caixa de aprovacoes (P2.28, {@code wf.self_approval} sempre falso); aqui fica
 * a guarda local da classificacao, e a caixa da P2.28 e a fronteira de apresentacao.
 *
 * <p>{@code situation} espelha {@code PaymentSituation}; {@code effectType} espelha o
 * efeito decidido ({@code SETTLEMENT}, {@code NEXT_BILL_DEBIT}, {@code NEXT_BILL_CREDIT},
 * {@code DUPLICATE_REFUND_HANDOFF}, {@code CREDIT_RECOVERY}, {@code TRANSFER},
 * {@code UNIDENTIFIED_QUEUE}, {@code NONE_PENDING_MANUAL}).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "payment_classifications")
@Getter
@Setter
public class PaymentClassificationEntity extends TenantAwareEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "situation", nullable = false, length = 40)
    private String situation;

    /** AUTOMATICA ou MANUAL. Manual exige aprovacao dupla antes do efeito. */
    @Column(name = "origin", nullable = false, length = 12)
    private String origin;

    /** PROPOSTA, APROVADA, REJEITADA, APLICADA. */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "PROPOSTA";

    @Column(name = "effect_type", nullable = false, length = 30)
    private String effectType;

    @Column(name = "effect_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal effectAmount = new BigDecimal("0.00");

    /** Conta de destino quando o efeito e transferencia (OS PAYMENT_TRANSFER). */
    @Column(name = "target_bill_id")
    private UUID targetBillId;

    /** OS ligada ao efeito (PAYMENT_TRANSFER, REFUND_REQUEST da P3.29 etc.). */
    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "first_approver_id")
    private UUID firstApproverId;

    @Column(name = "second_approver_id")
    private UUID secondApproverId;
}
