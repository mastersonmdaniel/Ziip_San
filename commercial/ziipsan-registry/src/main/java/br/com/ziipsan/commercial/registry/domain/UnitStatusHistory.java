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
     * P1.3 (V3_12): service_order_id é NOT NULL, com FK para service_orders
     * desde a V9_1 (fk_ush_service_order) — a correção R1 ("com a FK") já
     * estava satisfeita pela V9_1; a P1.3 entregou o NOT NULL que faltava.
     * Antes da P1.3 ele era NULL admitido ("nullable until P1.3"); a V3_6
     * reservada no plano já existia (P0.16), então o NOT NULL chegou na
     * V3_12 (a V9_2 do tenant é da P3.4). A escrita direta sem OS (ex.:
     * criar o imóvel escrevia o histórico inicial sem OS) deixa de ser
     * válida: a escrita roteia por OS interna.
     */
    /** P1.3 (V3_12): NOT NULL no banco — toda linha nasce de uma OS. */
    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    public enum ServiceKind { WATER, SEWER }

}
