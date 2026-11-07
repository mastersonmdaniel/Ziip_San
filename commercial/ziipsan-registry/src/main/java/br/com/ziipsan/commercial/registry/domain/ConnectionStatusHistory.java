package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Histórico de mudanças de situação da LIGAÇÃO (somente inserção),
 * substituindo {@code unit_status_history} na GIS-05 (P2.23, V3_14;
 * DAD-09). Cada linha guarda a situação anterior e a nova, o momento,
 * a OS obrigatória que aplicou a mudança e os
 * {@code parameterVersionIds} vigentes nela (JSONB, array de ids, como
 * na conta da P2.5). {@code consumerUnitId} preserva a proveniência:
 * nas linhas migradas, a unidade de onde o histórico veio; nas novas, a
 * unidade-âncora (imóvel) da ligação.
 *
 * <p>A FK de {@code serviceOrderId} para {@code service_orders} é da
 * V13_10: na cadeia de migrations a V3_14 roda antes da V9_1.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de
 * {@code BaseEntity}.
 */
@Entity
@Table(name = "connection_status_history")
@Getter
@Setter
public class ConnectionStatusHistory extends TenantAwareEntity {

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "consumer_unit_id")
    private UUID consumerUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private ConnectionStatus.Kind kind;

    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    /** JSON (array de ids de parameter_versions) serializado SÓ pela classe Json do core. */
    @Column(name = "parameter_version_ids", columnDefinition = "jsonb")
    private String parameterVersionIds;
}
