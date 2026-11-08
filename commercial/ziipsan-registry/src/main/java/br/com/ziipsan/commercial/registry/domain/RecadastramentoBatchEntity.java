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
 * Lote de recadastramento persistido (ED-16): a linha nasce OPEN quando
 * a fila da P3.32 abre a OS interna de lote e so chega a CLOSED no
 * encerramento executado dela (a maquina de estados vive em
 * {@code RecadastramentoBatch}; esta entidade e o espelho persistido).
 * Itens da fila e unidades alvo vao em JSON pela classe Json do core.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "recadastramento_batches")
@Getter
@Setter
public class RecadastramentoBatchEntity extends TenantAwareEntity {

    /** OS interna de lote (ADR-0039) que o lote e. Sem ela, nao ha lote. */
    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    /** JSON (classe Json do core): ids dos itens da fila da P3.32 agrupados. */
    @Column(name = "queue_item_ids_json", nullable = false, columnDefinition = "text")
    private String queueItemIdsJson;

    /** JSON (classe Json do core): ids das unidades alvo do lote. */
    @Column(name = "target_unit_ids_json", nullable = false, columnDefinition = "text")
    private String targetUnitIdsJson;

    /** JSON (classe Json do core): unidades com o efeito da OS confirmado. */
    @Column(name = "confirmed_unit_ids_json", columnDefinition = "text")
    private String confirmedUnitIdsJson;

    /** OPEN ou CLOSED (RecadastramentoBatch.Status). */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
