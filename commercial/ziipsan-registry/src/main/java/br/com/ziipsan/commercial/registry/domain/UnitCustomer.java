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
 * Vinculo temporal cliente-imovel: proprietario, usuario ou responsavel. endDate nulo = vigente.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "unit_customers")
@Getter
@Setter
public class UnitCustomer extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 12)
    private Role role;

    @Column(name = "start_date", nullable = false)
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @Column(name = "end_reason", length = 200)
    private String endReason;

    @Column(name = "show_on_bill", nullable = false)
    private boolean showOnBill;

    /**
     * P1.3 (V3_12): papel por catálogo (relation_roles). {@code role} FICA como
     * espelho do system_role (remoção na P1.41); o serviço grava os dois juntos.
     */
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    /** P1.3 (V3_12): motivo de fim por catálogo (unit_link_end_reasons). */
    @Column(name = "end_reason_id")
    private UUID endReasonId;

    /** P1.3 (V3_12): OS que abriu o vínculo (FK na V9_3). */
    @Column(name = "opened_by_service_order_id")
    private UUID openedByServiceOrderId;

    /** P1.3 (V3_12): OS que fechou o vínculo (FK na V9_3). */
    @Column(name = "closed_by_service_order_id")
    private UUID closedByServiceOrderId;

    /** P1.3 (V3_12): forma de entrega da conta (obrigatória quando há responsável). */
    @Column(name = "delivery_mode_id")
    private UUID deliveryModeId;

    public enum Role { OWNER, USER, RESPONSIBLE }

}
