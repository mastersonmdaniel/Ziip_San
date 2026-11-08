package br.com.ziipsan.intelligence.losses.dmc;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Setorizacao (ED-85; COE-11): criacao de DMC sob a regra da fonte
 * unica — a zona referenciada tem de existir em {@code gis_zone} com
 * {@code zone_type='DMA'}, o codigo tem de ser unico no tenant e a
 * zona nao pode ja sustentar outro DMC (um DMC por zona DMA). As
 * conferencias vivem em PORTAS: o GIS continua dono da zona e o
 * registro operacional dono do {@code dma_district}; este servico nao
 * duplica nenhum dos dois cadastros.
 */
public final class DmcSectorService {

    /** Porta do GIS (V13_2): devolve o zone_type da zona, vazio se inexistente no tenant. */
    public interface GisZonePort {
        Optional<String> zoneType(UUID tenantId, UUID gisZoneId);
    }

    /** Porta do registro operacional de DMCs ja criados neste modulo. */
    public interface DmcRegistryPort {
        boolean codeExists(UUID tenantId, String code);
        boolean zoneAlreadyHasDmc(UUID tenantId, UUID gisZoneId);
    }

    public static final String DMA_ZONE_TYPE = "DMA";

    private final GisZonePort zones;
    private final DmcRegistryPort registry;

    public DmcSectorService(GisZonePort zones, DmcRegistryPort registry) {
        this.zones = Objects.requireNonNull(zones, "porta de zonas do GIS obrigatoria");
        this.registry = Objects.requireNonNull(registry, "porta de registro de DMC obrigatoria");
    }

    public DmcDistrict createDmc(UUID id, UUID tenantId, UUID dmaDistrictId, UUID gisZoneId,
                                 String code, String sectorCode, int connections,
                                 Double mainsLengthKmOrNull) {
        Optional<String> type = zones.zoneType(tenantId, gisZoneId);
        if (type.isEmpty()) throw new IllegalArgumentException(
                "zona DMA inexistente em gis_zone para o tenant: " + gisZoneId);
        if (!DMA_ZONE_TYPE.equals(type.get())) throw new IllegalArgumentException(
                "zona de gis_zone nao e DMA (zone_type=" + type.get() + "): o DMC so nasce sobre zona DMA");
        if (registry.codeExists(tenantId, code)) throw new IllegalArgumentException(
                "codigo de DMC ja existe no tenant: " + code);
        if (registry.zoneAlreadyHasDmc(tenantId, gisZoneId)) throw new IllegalArgumentException(
                "zona DMA ja sustenta outro DMC (fonte unica: um DMC por zona): " + gisZoneId);
        return new DmcDistrict(id, tenantId, dmaDistrictId, gisZoneId, code, sectorCode,
                connections, mainsLengthKmOrNull);
    }

    /** DMC ativo e com macromedidor de entrada esta apto ao balanco mensal. */
    public static void requireBalanceReady(DmcDistrict dmc) {
        Objects.requireNonNull(dmc, "DMC obrigatorio");
        if (dmc.status() != DmcDistrict.Status.ACTIVE)
            throw new IllegalStateException("balanco exige DMC ativo: " + dmc.code());
        if (!dmc.hasInletMeter())
            throw new IllegalStateException("balanco exige macromedidor de entrada vinculado (ED-17): " + dmc.code());
        if (dmc.connections() <= 0)
            throw new IllegalStateException("balanco por ligacao exige ligacoes > 0 no DMC: " + dmc.code());
    }
}
