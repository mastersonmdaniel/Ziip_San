package br.com.ziipsan.intelligence.gis.validation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registros de leitura da validação contínua (GIS-03, P2.21, §5.3 a §5.5).
 *
 * <p>São o retrato das feições de uma área suja já resolvido contra o
 * catálogo (tipo, classe, domínio, item e material), para que o motor de
 * regras ({@link GisValidationEngine}) seja lógica pura, sem banco: quem lê
 * o banco é o {@link GisValidationService}, quem decide é o motor, e os
 * testes exercitam o motor com estes registros em memória.
 *
 * <p>{@code dnMm}/{@code dn2Mm} e {@code materialCode} vêm do item de
 * catálogo da feição ({@code gis_catalog_item} + {@code gis_material}),
 * nunca de texto livre da feição — é o desenho do §3.2.
 */
public final class GisValidationModel {

    private GisValidationModel() {
    }

    /** Nó ({@code gis_node}) com tipo, classe e item resolvidos. */
    public record NodeFeature(UUID id, UUID typeId, String typeCode, String classCode,
                              String domain, String topologyRole, String terminalConfig,
                              String lifecycleStatus, UUID catalogItemId,
                              Integer dnMm, Integer dn2Mm, String materialCode, boolean legacyOnly,
                              String normalStatus, String operationalStatus,
                              BigDecimal invertElevationM,
                              String captureMethod, String accuracyStatus, UUID changeSetId) {
    }

    /** Arco ({@code gis_arc}) com tipo, classe e item resolvidos. */
    public record ArcFeature(UUID id, UUID typeId, String typeCode, String classCode,
                             String domain, String lifecycleStatus,
                             UUID node1Id, UUID node2Id,
                             Integer dnMm, String materialCode, boolean legacyOnly,
                             String networkFunction, Boolean pressurized, String flowDirection,
                             BigDecimal upstreamInvertM, BigDecimal downstreamInvertM,
                             String captureMethod, String accuracyStatus, UUID changeSetId) {
    }

    /** Ramal ({@code gis_connection}) com o arco de ligação resolvido pelo serviço. */
    public record ConnectionFeature(UUID id, String typeCode, String classCode, String domain,
                                    String lifecycleStatus, UUID arcId, UUID tapNodeId,
                                    UUID serviceConnectionId,
                                    Integer dnMm, String materialCode, boolean legacyOnly,
                                    String captureMethod, String accuracyStatus, UUID changeSetId) {
    }

    /**
     * Metadados de captura de feições sem papel topológico na validação
     * ({@code gis_structure}, {@code gis_zone}, {@code gis_poi}): só entram
     * nas regras de captura, exatidão e material legado do §5.4.
     */
    public record MetaFeature(String featureTable, UUID id,
                              Integer dnMm, boolean legacyOnly,
                              String captureMethod, String accuracyStatus, UUID changeSetId) {
    }

    /**
     * Regra de conectividade de {@code gis_connectivity_rule} com os códigos
     * de tipo/classe resolvidos (a linha guarda ids; a comparação do motor é
     * por código, como o catálogo é semeado).
     */
    public record ConnectivityRule(String fromTypeCode, String fromClassCode,
                                   String toTypeCode, String toClassCode,
                                   String relation, Integer minArcs, Integer maxArcs,
                                   String conditionJson, String severity) {
    }

    /**
     * Problema detectado, ainda sem persistência: é a chave de deduplicação
     * de {@code gis_validation_issue} (regra + feição + segunda feição).
     * Igualdade do record é o que impede duplicar OPEN na reconciliação.
     *
     * <p>{@code message} (correção M6 da revisão da P2.21): a descrição do
     * problema que o serviço grava na coluna {@code message} de
     * {@code gis_validation_issue} na abertura. O motor não formulava
     * descrições; a mensagem nasce no ponto de detecção, a partir de
     * regra + feição, pelo construtor de conveniência abaixo e por
     * {@link #describe(String, String, UUID, UUID)} — determinística e sem
     * a severidade, para a igualdade do record continuar dependendo só de
     * regra, severidade e feições.
     */
    public record DetectedIssue(String ruleCode, String severity, String featureTable,
                                UUID featureId, UUID feature2Id, UUID changeSetId,
                                String message) {

        /** Detecção sem mensagem explícita: a mensagem é formulada de regra + feição. */
        public DetectedIssue(String ruleCode, String severity, String featureTable,
                             UUID featureId, UUID feature2Id, UUID changeSetId) {
            this(ruleCode, severity, featureTable, featureId, feature2Id, changeSetId,
                    describe(ruleCode, featureTable, featureId, feature2Id));
        }

        /** Descrição padrão do problema, em pt-BR, a partir da regra e das feições. */
        public static String describe(String ruleCode, String featureTable, UUID featureId, UUID feature2Id) {
            String detail = switch (ruleCode == null ? "" : ruleCode) {
                case "CONNECTIVITY_ARC_COUNT" -> "contagem de arcos fora do minimo/maximo da regra de conectividade";
                case "CONNECTIVITY_SAME_DN" -> "arcos ligados com DN diferentes em peca que exige o mesmo DN";
                case "CONNECTIVITY_REDUCER_DN" -> "DNs dos arcos diferentes de DN/DN2 do item da reducao";
                case "TEE_BRANCH_DN_MISMATCH" -> "ramal do te com DN diferente do DN2 do item, ou passantes com DN diferente do DN do item";
                case "DN_MISMATCH_WITHOUT_FITTING" -> "mudanca de DN ou de material em juncao sem peca";
                case "CONNECTION_TO_TRANSMISSION" -> "ramal ligado a adutora ou subadutora";
                case "HYDRANT_NOT_CONNECTED" -> "hidrante sem ligacao a rede por ramal de hidrante ou te";
                case "DOMAIN_MISMATCH" -> "arco e no de dominios diferentes";
                case "MANHOLE_FLOW" -> "PV, TIL ou caixa sem arco de entrada ou sem arco de saida";
                case "INLET_NOT_CONNECTED" -> "boca de lobo sem arco para PV pluvial ou galeria";
                case "NODE_WITHOUT_ARC" -> "no sem arco";
                case "ARC_WITHOUT_NODE" -> "arco em operacao ou em obra sem no numa das pontas";
                case "ISLAND_WITHOUT_SOURCE" -> "componente de agua sem fonte";
                case "VALVE_WITHOUT_STATE" -> "valvula sem estado normal ou operacional";
                case "NEGATIVE_SLOPE" -> "declividade negativa em arco de gravidade";
                case "GRAVITY_CYCLE" -> "ciclo em rede por gravidade";
                case "MANHOLE_WITHOUT_INVERT" -> "PV sem cota de fundo";
                case "CONNECTION_WITHOUT_LINK" -> "ramal sem ligacao";
                case "MISSING_CAPTURE_METADATA" -> "feicao sem metodo de captura";
                case "ACCURACY_OUT_OF_TARGET" -> "exatidao fora da meta";
                case "LEGACY_MATERIAL_ON_NEW_FEATURE" -> "material legado em feicao nova";
                case "INVALID_GEOMETRY" -> "geometria invalida";
                case "SELF_INTERSECTING_ARC" -> "arco com autointersecao";
                case "DUPLICATE_OR_OVERLAPPING_ARC" -> "arcos duplicados ou sobrepostos";
                default -> "problema de validacao";
            };
            StringBuilder sb = new StringBuilder(detail)
                    .append(" (").append(ruleCode).append(") em ")
                    .append(featureTable).append(' ').append(featureId);
            if (feature2Id != null) {
                sb.append(", relacionado a ").append(feature2Id);
            }
            return sb.append('.').toString();
        }
    }
}
