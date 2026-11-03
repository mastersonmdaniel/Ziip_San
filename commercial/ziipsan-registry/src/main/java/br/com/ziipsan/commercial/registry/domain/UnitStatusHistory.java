package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Historico de mudancas de situacao de agua ou esgoto (somente insercao).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unit_status_history")
@Getter
@Setter
public class UnitStatusHistory extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_kind", nullable = false, length = 5)
    private ServiceKind serviceKind;

    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;

    @Column(name = "changed_at", nullable = false)
    private java.time.OffsetDateTime changedAt;

    @Column(name = "reason", length = 200)
    private String reason;

    /**
     * P1.31 (V9_1): FK para service_orders e NOT NULL (ADR-0039 item 6).
     * Todo historico nasce da OS que causou a mudanca; a escrita direta sem
     * OS e recusada pelo banco. A migracao do registro direto restante
     * para OS e da P1.3 (V3_6).
     */
    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    public enum ServiceKind { WATER, SEWER }

}
