package br.com.ziipsan.intelligence.gis.ogc;

import java.util.UUID;

/**
 * Porta de leitura de parâmetros do tenant (correção ED-67, D7):
 * o piso de agregação externa é o parâmetro de catálogo
 * {@code gis.public_aggregation_min_units} DO TENANT (§3.10) — os
 * endpoints NÃO podem fixá-lo em 5 no código, ou uma célula que o
 * tenant mandou suprimir seria publicada. Sem a porta ligada, os
 * serviços usam o parâmetro que o chamador passar (os controllers
 * passam 5, o piso da classe c); com a porta, o valor do tenant
 * prevalece e o {@code GisExportGuard} só o amplia até o piso.
 *
 * <p>A implementação de referência é
 * {@link GisTenantParameterReader} (mesma leitura da P3.27 sobre
 * {@code parameter_versions}); a ligação do {@code DataSource} é da
 * integração.
 */
public interface GisTenantParameterPort {

    /** Valor vigente de {@code gis.public_aggregation_min_units} do tenant, ou {@code null} se não configurado. */
    Integer aggregationMinUnits(UUID tenantId);
}
