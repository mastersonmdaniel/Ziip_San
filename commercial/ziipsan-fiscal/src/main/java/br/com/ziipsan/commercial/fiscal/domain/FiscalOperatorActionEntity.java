package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Trilha de ações do operador sobre documento fiscal do tenant (V4_11, P2.33):
 * reenvio, inutilização de numeração e pedido de cancelamento, com permissão,
 * motivo, ambiente e desfecho. Inutilização não tem documento (a faixa é de números
 * NÃO usados) — por isso {@code fiscalDocumentId} é nulo nela e a faixa vai nas
 * colunas próprias.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_operator_actions")
@Getter
@Setter
public class FiscalOperatorActionEntity extends TenantAwareEntity {

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "fiscal_document_id")
    private UUID fiscalDocumentId;

    @Column(name = "issuer_id")
    private UUID issuerId;

    @Column(name = "serie")
    private Integer serie;

    @Column(name = "number_from")
    private Long numberFrom;

    @Column(name = "number_to")
    private Long numberTo;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "permission_code", nullable = false, length = 60)
    private String permissionCode;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "tp_amb")
    private Integer tpAmb;

    @Column(name = "protocol", length = 100)
    private String protocol;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;
}
