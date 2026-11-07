package br.com.ziipsan.operations.metering.hydrometry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

/** Recebimento de lote (ED-14): a entrada do hidrômetro no parque nasce aqui. */
@Entity @Table(name = "meter_lots") @Getter @Setter
public class MeterLot extends TenantAwareEntity {
    @Column(name = "lot_code", nullable = false, length = 40) private String lotCode;
    @Column(name = "model_id", nullable = false) private UUID modelId;
    @Column(name = "received_on", nullable = false) private LocalDate receivedOn;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "storage_location_id", nullable = false) private UUID storageLocationId;
    @Column(name = "bench_test_id") private UUID benchTestId;
    @Column(name = "approved") private Boolean approved;
}
