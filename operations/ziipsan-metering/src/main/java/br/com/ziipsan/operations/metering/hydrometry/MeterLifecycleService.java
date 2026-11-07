package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.domain.Meter;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.MovementType;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.RemovalDestination;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Serviço do ciclo de vida (ED-14): ÚNICO caminho de mudança de estado do hidrômetro
 * (o {@code Meter} não expõe setter público de status — ver A4). Persiste via portas;
 * toda transição grava a movimentação (histórico) na mesma operação.
 *
 * <p>Correções da revisão cruzada incorporadas:
 * <ul>
 *   <li><b>A2</b> — cadastro novo recusa os campos de texto livre legados
 *       (brand/model/inmetroApproval/metrologicalClass preenchidos = erro);</li>
 *   <li><b>A3</b> — {@code receive()} sobre hidrômetro que JÁ tem estado é recusado
 *       (re-recebimento não apaga INSTALLED sem OS); lote é obrigatório e o modelo
 *       do lote precisa ser o modelo do recebimento;</li>
 *   <li><b>A1</b> — recebimento força {@code migratedPark = false} e chama
 *       {@code requireLegacyClassOnlyOnMigratedPark} no caminho de escrita: parque
 *       migrado NÃO nasce por esta porta (nasce só pela migration);</li>
 *   <li><b>B2</b> — INSTALL/REINSTALL exigem {@code installationId} (a instalação
 *       criada por {@code MeterInstallationService}).</li>
 * </ul>
 */
public class MeterLifecycleService {

    public interface MeterStore {
        Meter find(UUID tenantId, UUID meterId);
        void save(Meter meter);
    }
    public interface MovementStore {
        void append(UUID tenantId, UUID meterId, MovementType type, LocalDate on, UUID serviceOrderId,
                    UUID installationId, UUID storageLocationId, RemovalDestination destination, String writeOffReason);
    }
    /** Catálogo de lotes: modelo do lote (null = lote inexistente). */
    public interface LotCatalog { UUID modelIdOfLot(UUID tenantId, UUID lotId); }

    private final MeterStore meters;
    private final MovementStore movements;
    private final LotCatalog lots;

    public MeterLifecycleService(MeterStore meters, MovementStore movements, LotCatalog lots) {
        this.meters = meters; this.movements = movements; this.lots = lots;
    }

    /** Recebimento de lote: nasce AVAILABLE, com modelo do catálogo, lote e armazenagem. */
    public void receive(UUID tenantId, Meter meter, UUID lotId, UUID modelId, UUID storageLocationId, LocalDate on) {
        if (meter.getStatus() != null)
            throw new IllegalArgumentException("Hidrômetro já recebido (estado " + meter.getStatus()
                    + "): estado só muda por instalação, retirada ou movimentação com OS — recebimento duplicado recusado");
        if (lotId == null) throw new IllegalArgumentException("Recebimento é DE LOTE: lotId é obrigatório");
        if (modelId == null) throw new IllegalArgumentException("Modelo aprovado do catálogo é obrigatório (texto livre abolido na ED-14)");
        UUID lotModel = lots.modelIdOfLot(tenantId, lotId);
        if (lotModel == null) throw new IllegalArgumentException("Lote inexistente para o tenant: " + lotId);
        if (!lotModel.equals(modelId)) throw new IllegalArgumentException(
                "Modelo do recebimento (" + modelId + ") difere do modelo do lote (" + lotModel + ")");
        if (storageLocationId == null) throw new IllegalArgumentException("Local de armazenagem é obrigatório no recebimento");
        requireNoLegacyFreeText(meter);
        LegacyMetrologicalClassRule.requireNewRegistrationFreeOfLegacyClass(meter.getLegacyMetrologicalClass());
        meter.setMigratedPark(false);
        LegacyMetrologicalClassRule.requireLegacyClassOnlyOnMigratedPark(meter.getLegacyMetrologicalClass(), meter.isMigratedPark());
        meter.setModelId(modelId); meter.setLotId(lotId); meter.setStorageLocationId(storageLocationId);
        meter.transitionTo(MeterLifecycleRule.requireTransition(null, MovementType.RECEIPT, null, null, storageLocationId, null));
        meters.save(meter);
        movements.append(tenantId, meter.getId(), MovementType.RECEIPT, on, null, null, storageLocationId, null, null);
    }

    /** A2: no caminho novo, os campos de texto livre legados não são graváveis — preenchidos, é erro. */
    static void requireNoLegacyFreeText(Meter meter) {
        if (isFilled(meter.getBrand()) || isFilled(meter.getModel()) || isFilled(meter.getInmetroApproval())
                || isFilled(meter.getMetrologicalClass()))
            throw new IllegalArgumentException("Cadastro novo usa o catálogo (marca/modelo/portaria/classe via meter_models): "
                    + "brand/model/inmetroApproval/metrologicalClass de texto livre não são aceitos (ED-14)");
    }
    private static boolean isFilled(String s) { return s != null && !s.isBlank(); }

    /** Instalação, retirada, reinstalação, manutenção, baixa, furto — sempre com OS. */
    public Meter.Status move(UUID tenantId, UUID meterId, MovementType type, RemovalDestination destination,
                             UUID serviceOrderId, UUID installationId, UUID storageLocationId,
                             String writeOffReason, LocalDate on) {
        if ((type == MovementType.INSTALL || type == MovementType.REINSTALL) && installationId == null)
            throw new IllegalArgumentException("Instalação exige a instalação registrada (installationId) — ver MeterInstallationService (B2)");
        Meter meter = meters.find(tenantId, meterId);
        if (meter == null) throw new IllegalArgumentException("Hidrômetro inexistente para o tenant: " + meterId);
        UUID resultingStorage = type == MovementType.INSTALL || type == MovementType.REINSTALL ? null
                : (storageLocationId != null ? storageLocationId : meter.getStorageLocationId());
        String resultingReason = type == MovementType.WRITE_OFF
                || (type == MovementType.REMOVE && destination == RemovalDestination.SCRAP)
                ? writeOffReason : meter.getWriteOffReason();
        Meter.Status target = MeterLifecycleRule.requireTransition(meter.getStatus(), type, destination,
                serviceOrderId, resultingStorage, resultingReason);
        meter.transitionTo(target);
        meter.setStorageLocationId(target == Meter.Status.INSTALLED ? null : resultingStorage);
        if (target == Meter.Status.WRITTEN_OFF) meter.setWriteOffReason(resultingReason);
        meters.save(meter);
        movements.append(tenantId, meterId, type, on, serviceOrderId, installationId,
                meter.getStorageLocationId(), destination, target == Meter.Status.WRITTEN_OFF ? resultingReason : null);
        return target;
    }
}
