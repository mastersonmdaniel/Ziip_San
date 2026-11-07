package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Instalação de um hidrômetro numa unidade consumidora, com a leitura de instalação e, quando
 * retirado, a de retirada. Histórico temporal: o intervalo {@code [installedOn, removedOn)} não se
 * sobrepõe, por unidade e tipo e por hidrômetro (constraints de exclusão no banco).
 *
 * <p>{@code unitId} referencia {@code consumer_units} (módulo registry) por UUID, com FK composta
 * {@code (tenant_id, unit_id)}.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "meter_installations")
@Getter
@Setter
public class MeterInstallation extends TenantAwareEntity {

    @Column(name = "meter_id", nullable = false)
    private UUID meterId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 5)
    private MeasurementKind kind;

    @Column(name = "installed_on", nullable = false)
    private LocalDate installedOn;

    @Column(name = "installed_reading", nullable = false)
    private int installedReading;

    @Column(name = "removed_on")
    private LocalDate removedOn;

    @Column(name = "removed_reading")
    private Integer removedReading;

    @Column(name = "is_replacement", nullable = false)
    private boolean replacement;

    @Column(name = "seal_number", length = 40)
    private String sealNumber;

    @Column(name = "protection_type", length = 40)
    private String protectionType;

    @Column(name = "location_description", length = 100)
    private String locationDescription;

    // --- ED-14: ligacao (P2.23), OS de instalacao/retirada, protecao e local por catalogo ---
    @Column(name = "service_connection_id")
    private UUID serviceConnectionId;

    @Column(name = "install_service_order_id")
    private UUID installServiceOrderId;

    @Column(name = "remove_service_order_id")
    private UUID removeServiceOrderId;

    @Column(name = "protection_catalog_id")
    private UUID protectionCatalogId;

    @Column(name = "install_location_catalog_id")
    private UUID installLocationCatalogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_destination", length = 20)
    private br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.RemovalDestination removalDestination;

}
