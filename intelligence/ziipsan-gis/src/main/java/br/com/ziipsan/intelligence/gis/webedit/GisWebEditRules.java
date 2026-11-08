package br.com.ziipsan.intelligence.gis.webedit;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras puras da edição web completa (GIS-09, ED-53), espelho
 * legível das guardas do SQL em db/pendente — o banco é a
 * autoridade; esta classe existe para a api falhar cedo e para as
 * regras serem testáveis sem banco.
 *
 * <p>Heranças respeitadas: (1) toda edição da rede em operação
 * passa por pacote da GIS-04 (P2.22, V13_9) — o editor NUNCA grava
 * feição IN_SERVICE/UNDER_CONSTRUCTION direto; (2) o cache de
 * tiles da P3.25 é por tenant — nenhuma chave/regra aqui mistura
 * tenants; (3) a pendência 12 da ED-55 (como o endpoint define o
 * GUC a partir do mapeamento de papéis) NÃO é resolvida aqui: o
 * serviço segue o padrão de contexto existente
 * (TenantContextHolder) e a pendência é marcada no relatório.
 */
public final class GisWebEditRules {

    /** Operações do editor (SG-864/SG-865: permissão por operação). */
    public static final Set<String> OPERATIONS = Set.of(
            "VIEW", "CREATE", "EDIT_GEOMETRY", "EDIT_ATTRIBUTES",
            "SPLIT", "MERGE", "INSERT_NODE", "DELETE", "MANAGE_LAYER");

    /** Geometrias editáveis por classe de camada. */
    public static final Map<String, Set<String>> GEOMETRY_BY_KIND = Map.of(
            "NODE", Set.of("POINT"),
            "ARC", Set.of("LINESTRING"),
            "CONNECTION", Set.of("LINESTRING"),
            "STRUCTURE", Set.of("POLYGON", "POINT"),
            "ZONE", Set.of("POLYGON"),
            "USER", Set.of("POINT", "LINESTRING", "POLYGON"));

    /** Tipos de pacote (V13_9) que o editor web pode abrir/usar. */
    public static final Set<String> EDITOR_CHANGE_SET_TYPES = Set.of(
            "PROJECT", "AS_BUILT", "CORRECTION", "SERVICE_ORDER");

    /**
     * Cadastro de unidades com geometria (SG-148 a SG-152): os
     * tipos de estrutura (gis_structure) desta entrega. ETA =
     * estação de tratamento de água; ETE = de esgoto; ELEVATORY =
     * elevatória (água ou esgoto, pelo domínio); RESERVOIR =
     * reservatório. Os códigos são os do domínio desta entrega —
     * o vínculo com o factory_code do catálogo é conferido na
     * integração (pendência declarada, nenhum código de fábrica
     * foi inventado).
     */
    public static final Set<String> PLANT_STRUCTURE_KINDS = Set.of(
            "ETA", "ETE", "ELEVATORY", "RESERVOIR");

    /** Junção de tabelas (SG-841): tabelas/classes permitidas. */
    public static final Set<String> JOINABLE_TABLES = Set.of(
            "gis_node", "gis_arc", "gis_connection", "gis_structure",
            "gis_zone", "consumer_units");

    public static final Set<String> JOIN_TYPES = Set.of(
            "INNER", "LEFT");

    public static final Set<String> JOIN_KEYS = Set.of(
            "feature_id", "service_connection_id", "consumer_unit_id",
            "code", "dma_id");

    private GisWebEditRules() {
    }

    /** SG-864/SG-865: permissão por camada E por operação. */
    public static boolean canOperate(Set<String> grantedOperations,
                                     String operation) {
        if (operation == null || !OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException(
                    "operacao de edicao desconhecida: " + operation);
        }
        return grantedOperations != null
                && grantedOperations.contains(operation);
    }

    public static void ensureCanOperate(Set<String> grantedOperations,
                                        String operation) {
        if (!canOperate(grantedOperations, operation)) {
            throw new LayerPermissionException(operation);
        }
    }

    /** Edição de rede exige pacote aberto (DRAFT) do tipo certo. */
    public static void ensureEditableChangeSet(String type, String status) {
        if (type == null || !EDITOR_CHANGE_SET_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "tipo de pacote nao editavel pelo editor web: " + type);
        }
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException(
                    "edicao web exige pacote em DRAFT (atual: " + status + ")");
        }
    }

    /**
     * TR-626: OS que altera a rede abre pacote. "Altera a rede" =
     * a OS declara efeito sobre feição GIS (rede), não qualquer
     * OS: OS de leitura/atendimento sem efeito de rede não abre
     * pacote (evita pacote vazio obrigatório).
     */
    public static boolean serviceOrderOpensChangeSet(boolean affectsNetwork) {
        return affectsNetwork;
    }

    public static String changeSetTypeForServiceOrder() {
        return "SERVICE_ORDER";
    }

    /** SG-858/TR-1050/TR-1051: projeto e as-built são pacotes distintos. */
    public static void ensureProjectPair(String projectType, String asBuiltType) {
        if (!"PROJECT".equals(projectType) || !"AS_BUILT".equals(asBuiltType)) {
            throw new IllegalArgumentException(
                    "projeto exige pacote PROJECT e pacote AS_BUILT proprios");
        }
    }

    /** Geometria compatível com a classe da camada. */
    public static void ensureGeometryFits(String layerKind, String geometryType) {
        Set<String> allowed = GEOMETRY_BY_KIND.get(layerKind);
        if (allowed == null) {
            throw new IllegalArgumentException(
                    "classe de camada desconhecida: " + layerKind);
        }
        if (geometryType == null || !allowed.contains(geometryType)) {
            throw new IllegalArgumentException("geometria " + geometryType
                    + " incompativel com a camada " + layerKind);
        }
    }

    /** SG-148..152: estrutura de planta exige kind conhecido. */
    public static void ensurePlantStructure(String structureKind) {
        if (structureKind == null
                || !PLANT_STRUCTURE_KINDS.contains(structureKind)) {
            throw new IllegalArgumentException(
                    "tipo de unidade desconhecido: " + structureKind);
        }
    }

    /** SG-840: camada do usuário é do tenant e do dono; nunca da rede. */
    public static void ensureUserLayerOwnedBy(UUID layerTenantId, UUID tenantId,
                                              UUID layerOwnerId, UUID userId) {
        if (!tenantId.equals(layerTenantId)) {
            throw new LayerPermissionException("VIEW");
        }
        if (!userId.equals(layerOwnerId)) {
            throw new LayerPermissionException("MANAGE_LAYER");
        }
    }

    /** SG-841: junção só entre tabelas/chaves da lista e no tenant. */
    public static void ensureJoinAllowed(String leftTable, String rightTable,
                                         String joinType, String joinKey) {
        if (!JOINABLE_TABLES.contains(leftTable)
                || !JOINABLE_TABLES.contains(rightTable)) {
            throw new IllegalArgumentException("tabela fora da lista de juncao");
        }
        if (joinType == null || !JOIN_TYPES.contains(joinType)) {
            throw new IllegalArgumentException("tipo de juncao nao permitido");
        }
        if (joinKey == null || !JOIN_KEYS.contains(joinKey)) {
            throw new IllegalArgumentException("chave de juncao nao permitida");
        }
    }

    /** 403 da api para camada/operação sem permissão (SG-864/865). */
    public static final class LayerPermissionException extends RuntimeException {
        public LayerPermissionException(String operation) {
            super("sem permissao na camada para a operacao " + operation
                    + " (SG-864/SG-865)");
        }
    }
}
