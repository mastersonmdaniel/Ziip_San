package br.com.ziipsan.reporting.registry;

import java.util.List;
import java.util.UUID;

/**
 * Porta de persistencia dos registros externos (correcao G5): a
 * implementacao de producao grava em {@code report_definitions} com o
 * {@code tenant_id} do dono (ver JdbcExternalDefinitionStore); a de
 * memoria serve aos testes e ao uso sem banco. Sem esta porta o registro
 * da P3.31 vivia so num mapa estatico e sumia no restart.
 */
public interface ExternalDefinitionStore {

    void save(UUID tenantId, ReportDefinition definition);

    List<ReportDefinition> loadByTenant(UUID tenantId);
}
