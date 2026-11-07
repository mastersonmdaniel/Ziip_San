package br.com.ziipsan.commercial.registry.legacy;

import java.util.UUID;

/**
 * P2.10: porta que grava a {@code occupancy_status} (CAD-68, P1.41) da
 * unidade já aplicada pelo framework da P1.2. A decisão do valor é a regra
 * pura {@link V1UnitSituation}; esta porta é só a escrita — a
 * implementação JDBC faz o UPDATE na unidade, e o
 * {@link V1MigrationStore} guarda o registro da decisão por lote
 * (tabela {@code legacy_import_unit_situations} da V14_2).
 */
public interface UnitSituationGateway {

    void applyOccupancyStatus(UUID tenantId, UUID unitId, String occupancyStatus, UUID actorId);
}
