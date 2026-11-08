package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Achado persistido e item da fila de correcao (P3.32): a linha nasce OPEN
 * na varredura e so chega a RESOLVED com o {@code serviceOrderId} da OS de
 * lote que a agrupou (a maquina de estados vive em
 * {@code CorrectionQueueItem}; esta entidade e o espelho persistido dela).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "cadastral_quality_findings")
@Getter
@Setter
public class CadastralQualityFindingEntity extends TenantAwareEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    /** Chave de deduplicacao da fila (regra|unidade|cliente), ver QualityFinding.key(). */
    @Column(name = "finding_key", nullable = false, length = 160)
    private String findingKey;

    @Column(name = "rule_code", nullable = false, length = 40)
    private String ruleCode;

    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    /** OPEN, BATCHED, RESOLVED ou DISMISSED (CorrectionQueueItem.Status). */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "OPEN";

    @Column(name = "service_order_id")
    private UUID serviceOrderId;

    @Column(name = "dismiss_reason", length = 300)
    private String dismissReason;
}
