package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Histórico único do hidrômetro (ED-14): recebimento, instalação, retirada (com destino),
 * reinstalação, manutenção e baixa — cada linha referencia a OS que a executou.
 * O lacre NÃO mora aqui nem em {@code meters}: lugar único é {@code meter_installations.seal_number}.
 */
@Entity @Table(name = "meter_movements") @Getter @Setter
public class MeterMovement extends TenantAwareEntity {
    @Column(name = "meter_id", nullable = false) private UUID meterId;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 30)
    private HydrometryEnums.MovementType movementType;
    @Column(name = "occurred_on", nullable = false) private LocalDate occurredOn;
    @Column(name = "service_order_id") private UUID serviceOrderId;
    @Column(name = "installation_id") private UUID installationId;
    @Column(name = "storage_location_id") private UUID storageLocationId;
    @Enumerated(EnumType.STRING) @Column(name = "removal_destination", length = 20)
    private HydrometryEnums.RemovalDestination removalDestination;
    @Column(name = "write_off_reason", length = 300) private String writeOffReason;
}
