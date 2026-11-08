package br.com.ziipsan.intelligence.gis.webedit;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do {@code attributes_schema} REAL do tipo de
 * feição (gis_feature_type, V13_1) — correção D2 da revisão
 * ED-53: o cadastro de unidades (SG-148..152) valida os atributos
 * contra o esquema cadastrado do tipo, lido do banco pela
 * implementação JDBC desta porta; validar contra um mapa vazio
 * (o defeito original) fingia validação que nunca acontecia.
 *
 * <p>Contrato: vazio = o tipo não existe NESTE tenant (toda
 * leitura é filtrada por tenant_id — sem vazamento cross-tenant).
 * Tipo existente sem esquema devolve o mapa do JSON gravado
 * (possivelmente vazio) e é o SERVIÇO quem recusa explicitamente
 * — nunca valida contra vazio em silêncio.
 */
public interface GisFeatureTypeSchemaPort {

    Optional<Map<String, Object>> attributesSchema(UUID tenantId,
                                                   UUID featureTypeId);
}
