package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Campo coletado do recadastramento persistido (ED-16): a linha nasce
 * COLLECTED na sincronizacao do app de campo e segue a maquina de
 * {@code CollectedField} (APPROVED/REJECTED campo a campo; APPLIED como
 * efeito da OS de lote; STALE/SUPERSEDED nas ocorrencias).
 *
 * <p>{@code old/new} do historico do cadastro sao REDACT na auditoria;
 * aqui, {@code value_at_collection}/{@code collected_value}/
 * {@code value_at_application} seguem a mesma cautela — e o valor de
 * documento, quando o campo e {@code CUSTOMER.document}, e sempre a
 * mascara, nunca o numero.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "recadastramento_collected_fields")
@Getter
@Setter
public class CollectedFieldEntity extends TenantAwareEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    /** Chave de idempotencia da sincronizacao (ED-18): um task_id, uma coleta. */
    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "customer_id")
    private UUID customerId;

    /** CUSTOMER ou CONSUMER_UNIT (mapa da P1.3). */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "field", nullable = false, length = 100)
    private String field;

    @Column(name = "value_at_collection", columnDefinition = "text")
    private String valueAtCollection;

    @Column(name = "collected_value", nullable = false, columnDefinition = "text")
    private String collectedValue;

    @Column(name = "collector_id", nullable = false)
    private UUID collectorId;

    /** JSON (classe Json do core): referencias das fotos da coleta. */
    @Column(name = "photo_refs_json", columnDefinition = "text")
    private String photoRefsJson;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    /** COLLECTED, APPROVED, REJECTED, APPLIED, STALE ou SUPERSEDED. */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "COLLECTED";

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_reason", length = 300)
    private String decisionReason;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    /** Linha de cadastral_change_requests gerada na aplicacao (com diferenca). */
    @Column(name = "applied_change_request_id")
    private UUID appliedChangeRequestId;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    /**
     * Valor vigente lido na tentativa de aplicacao que marcou STALE.
     * Pode ser nulo com o campo STALE: significa que o campo foi
     * esvaziado no cadastro depois da coleta (a porta de leitura
     * devolve null para campo sem valor) — por isso o SQL nao exige
     * NOT NULL desta coluna para STALE.
     */
    @Column(name = "value_at_application", columnDefinition = "text")
    private String valueAtApplication;
}
