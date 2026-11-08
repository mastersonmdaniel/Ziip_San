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
 * Historico do recadastramento persistido (ED-16): uma linha por
 * alteracao aprovada aplicada como efeito da OS de lote, apontando para
 * a linha irma em {@code cadastral_change_requests} (P1.3). Valores de
 * documento sao sempre a mascara.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "recadastramento_history")
@Getter
@Setter
public class RecadastramentoHistoryEntity extends TenantAwareEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    @Column(name = "collected_field_id", nullable = false)
    private UUID collectedFieldId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "field", nullable = false, length = 100)
    private String field;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "collector_id", nullable = false)
    private UUID collectorId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "change_request_id", nullable = false)
    private UUID changeRequestId;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;
}
