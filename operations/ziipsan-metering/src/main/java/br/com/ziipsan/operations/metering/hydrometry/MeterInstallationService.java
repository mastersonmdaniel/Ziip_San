package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.domain.MeterInstallation;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.MovementType;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.RemovalDestination;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Instalação e retirada como REGRA/SERVIÇO (ED-14, correção B2 — antes eram só colunas):
 * <ul>
 *   <li>instalação EXIGE ligação ({@code serviceConnectionId} — a ligação da P2.23),
 *       OS de instalação, proteção e local de instalação POR CATÁLOGO (ids validados
 *       contra o catálogo) e o LACRE (registrado aqui, lugar único);</li>
 *   <li>a instalação é gravada e o estado do hidrômetro muda pela MESMA operação via
 *       {@link MeterLifecycleService} (que exige o installationId);</li>
 *   <li>retirada EXIGE OS de retirada, data, leitura e destino (disponível/sucata),
 *       fecha a instalação e move o hidrômetro pela mesma operação.</li>
 * </ul>
 */
public class MeterInstallationService {

    public interface InstallationStore {
        MeterInstallation findOpen(UUID tenantId, UUID meterId);
        void save(MeterInstallation installation);
    }
    /** Catálogo único de proteção/local: devolve o kind do item (null = inexistente). */
    public interface InstallationCatalogLookup { String kindOf(UUID tenantId, UUID catalogId); }
    public interface ConnectionExistence { boolean exists(UUID tenantId, UUID serviceConnectionId); }

    private final InstallationStore installations;
    private final InstallationCatalogLookup catalog;
    private final ConnectionExistence connections;
    private final MeterLifecycleService lifecycle;

    public MeterInstallationService(InstallationStore installations, InstallationCatalogLookup catalog,
                                    ConnectionExistence connections, MeterLifecycleService lifecycle) {
        this.installations = installations; this.catalog = catalog;
        this.connections = connections; this.lifecycle = lifecycle;
    }

    public record InstallCommand(UUID meterId, UUID unitId, UUID serviceConnectionId, UUID installServiceOrderId,
                                 UUID protectionCatalogId, UUID installLocationCatalogId, String sealNumber,
                                 int installedReading, boolean replacement, LocalDate installedOn) {}
    public record RemoveCommand(UUID meterId, UUID removeServiceOrderId, RemovalDestination destination,
                                int removedReading, LocalDate removedOn, UUID destinationStorageLocationId,
                                String scrapReason) {}

    public MeterInstallation install(UUID tenantId, InstallCommand cmd) {
        if (cmd.serviceConnectionId() == null)
            throw new IllegalArgumentException("Instalação ligada à ligação: serviceConnectionId é obrigatório (P2.23/ED-14)");
        if (!connections.exists(tenantId, cmd.serviceConnectionId()))
            throw new IllegalArgumentException("Ligação inexistente para o tenant: " + cmd.serviceConnectionId());
        if (cmd.installServiceOrderId() == null)
            throw new IllegalArgumentException("Instalação exige a OS de instalação referenciada");
        if (cmd.unitId() == null) throw new IllegalArgumentException("Unidade consumidora da instalação é obrigatória");
        requireCatalogKind(tenantId, cmd.protectionCatalogId(), "PROTECTION", "proteção");
        requireCatalogKind(tenantId, cmd.installLocationCatalogId(), "INSTALL_LOCATION", "local de instalação");
        if (cmd.sealNumber() == null || cmd.sealNumber().isBlank())
            throw new IllegalArgumentException("Lacre da instalação é obrigatório (registrado num lugar só: a instalação)");
        if (installations.findOpen(tenantId, cmd.meterId()) != null)
            throw new IllegalArgumentException("Hidrômetro já tem instalação vigente: retire antes de instalar de novo");
        MeterInstallation inst = new MeterInstallation();
        inst.setTenantId(tenantId);
        inst.setMeterId(cmd.meterId()); inst.setUnitId(cmd.unitId());
        inst.setServiceConnectionId(cmd.serviceConnectionId());
        inst.setInstallServiceOrderId(cmd.installServiceOrderId());
        inst.setProtectionCatalogId(cmd.protectionCatalogId());
        inst.setInstallLocationCatalogId(cmd.installLocationCatalogId());
        inst.setSealNumber(cmd.sealNumber());
        inst.setInstalledReading(cmd.installedReading()); inst.setReplacement(cmd.replacement());
        inst.setInstalledOn(cmd.installedOn());
        inst.setKind(br.com.ziipsan.operations.metering.rules.MeasurementKind.WATER);
        installations.save(inst);
        lifecycle.move(tenantId, cmd.meterId(), cmd.replacement() ? MovementType.REINSTALL : MovementType.INSTALL,
                null, cmd.installServiceOrderId(), inst.getId(), null, null, cmd.installedOn());
        return inst;
    }

    public MeterInstallation remove(UUID tenantId, RemoveCommand cmd) {
        if (cmd.removeServiceOrderId() == null)
            throw new IllegalArgumentException("Retirada exige a OS de retirada referenciada");
        if (cmd.destination() == null)
            throw new IllegalArgumentException("Destino da retirada é obrigatório (disponível ou sucata)");
        MeterInstallation inst = installations.findOpen(tenantId, cmd.meterId());
        if (inst == null) throw new IllegalArgumentException("Hidrômetro sem instalação vigente para retirar: " + cmd.meterId());
        inst.setRemovedOn(cmd.removedOn()); inst.setRemovedReading(cmd.removedReading());
        inst.setRemoveServiceOrderId(cmd.removeServiceOrderId());
        inst.setRemovalDestination(cmd.destination());
        installations.save(inst);
        lifecycle.move(tenantId, cmd.meterId(), MovementType.REMOVE, cmd.destination(), cmd.removeServiceOrderId(),
                inst.getId(), cmd.destinationStorageLocationId(), cmd.scrapReason(), cmd.removedOn());
        return inst;
    }

    private void requireCatalogKind(UUID tenantId, UUID catalogId, String expectedKind, String label) {
        if (catalogId == null) throw new IllegalArgumentException("Catálogo de " + label + " é obrigatório (não é texto livre)");
        String kind = catalog.kindOf(tenantId, catalogId);
        if (!expectedKind.equals(kind)) throw new IllegalArgumentException(
                "Item de catálogo de " + label + " inexistente ou de outro tipo: " + catalogId);
    }
}
