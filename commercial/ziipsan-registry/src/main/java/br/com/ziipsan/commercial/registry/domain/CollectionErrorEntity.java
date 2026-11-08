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
 * Erro de coleta do recadastramento persistido (ED-16): uma linha por
 * submissao recusada na sincronizacao — a fonte do relatorio de erros
 * no recadastramento (TR-685/SG-911). O detalhe nunca carrega valor de
 * documento, nem mascarado.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "recadastramento_collection_errors")
@Getter
@Setter
public class CollectionErrorEntity extends TenantAwareEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "field", length = 100)
    private String field;

    /** Codigo do catalogo CollectionErrorCode. */
    @Column(name = "error_code", nullable = false, length = 30)
    private String errorCode;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    @Column(name = "collector_id")
    private UUID collectorId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
