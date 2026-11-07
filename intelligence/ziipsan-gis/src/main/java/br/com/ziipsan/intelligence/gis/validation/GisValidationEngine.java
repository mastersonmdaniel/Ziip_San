package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ArcFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectivityRule;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectionFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.MetaFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.NodeFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ServiceConnectionFeature;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Motor puro das validações do §5.3 e do §5.4 (GIS-03, P2.21).
 *
 * <p>Recebe o retrato da área (feições + regras de conectividade já
 * resolvidos pelo {@link GisValidationService}) e devolve os problemas,
 * sem tocar no banco. As validações que são geometria pura do PostGIS
 * (duplicados, autointerseção, {@code ST_IsValid}) ficam no serviço, em
 * SQL; tudo o que é topologia e atributo passa por aqui e é testável em
 * memória.
 *
 * <p>Códigos de regra ({@code rule_code} de {@code gis_validation_issue}):
 * <ul>
 *   <li>{@code CONNECTIVITY_ARC_COUNT} — contagem de arcos fora do
 *   min/max de {@code gis_connectivity_rule} (§5.3);</li>
 *   <li>{@code CONNECTIVITY_SAME_DN} — curva/luva com DN diferente nos
 *   dois arcos (§5.3);</li>
 *   <li>{@code CONNECTIVITY_REDUCER_DN} — redução cujos DNs dos arcos não
 *   são exatamente DN e DN2 do item (§5.3);</li>
 *   <li>{@code TEE_BRANCH_DN_MISMATCH} — tê cujo arco-ramal (o de DN
 *   diferente dos dois passantes, quando identificável) não tem o DN2 do
 *   item do tê, ou cujos passantes não têm o DN do item (ERROR; correção
 *   I3 da revisão da P2.21). Sem {@code dn2_mm} no item, o check não
 *   dispara: sem dado, sem problema;</li>
 *   <li>{@code DN_MISMATCH_WITHOUT_FITTING} — {@code JUNCTION} com
 *   mudança de DN ou de material sem peça (§5.3: WARNING — ver a nota de
 *   divergência no fim desta classe);</li>
 *   <li>{@code CONNECTION_TO_TRANSMISSION} — ramal ligado a adutora
 *   (ERROR) ou a subadutora (WARNING) (§5.3);</li>
 *   <li>{@code HYDRANT_NOT_CONNECTED} — hidrante sem ligação à rede de
 *   água por ramal de hidrante ou tê (§5.3);</li>
 *   <li>{@code DOMAIN_MISMATCH} — arco e nó de domínios diferentes, fora
 *   dos tipos de interface (§5.3);</li>
 *   <li>{@code MANHOLE_FLOW} — PV, TIL ou caixa sem arco de entrada ou
 *   sem arco de saída (§5.3 e §5.4);</li>
 *   <li>{@code INLET_NOT_CONNECTED} — boca de lobo sem arco para PV
 *   pluvial ou galeria (§5.3);</li>
 *   <li>{@code NODE_WITHOUT_ARC} — nó sem arco em tipo que exige arco (§5.4);</li>
 *   <li>{@code ARC_WITHOUT_NODE} — arco em operação ou em obra sem nó
 *   numa das pontas (§5.4, §5.1);</li>
 *   <li>{@code ISLAND_WITHOUT_SOURCE} — componente de água sem fonte
 *   (§5.4);</li>
 *   <li>{@code VALVE_WITHOUT_STATE} — válvula sem estado normal ou
 *   operacional (§5.4);</li>
 *   <li>{@code NEGATIVE_SLOPE} — declividade negativa em arco de
 *   gravidade (§5.4);</li>
 *   <li>{@code GRAVITY_CYCLE} — ciclo em rede por gravidade (§5.4);</li>
 *   <li>{@code MANHOLE_WITHOUT_INVERT} — PV sem cota de fundo (§5.4);</li>
 *   <li>{@code CONNECTION_WITHOUT_LINK} — ramal sem ligação (§5.4);</li>
 *   <li>{@code ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE} — ligação ativa
 *   sem ramal (GIS-05, P2.23; §5.4): ligação não encerrada, com papel
 *   de sistema diferente de {@code NO_CONNECTION}, e nenhum
 *   {@code gis_connection} não abandonado apontando para ela. O
 *   problema é ancorado numa "feição" sintética: a tabela
 *   {@code service_connections} entra como feature_table do problema
 *   para o painel tratar ligação como trata feição;</li>
 *   <li>{@code MISSING_CAPTURE_METADATA} — feição sem método de captura
 *   (§5.4);</li>
 *   <li>{@code ACCURACY_OUT_OF_TARGET} — exatidão fora da meta (§5.4);</li>
 *   <li>{@code LEGACY_MATERIAL_ON_NEW_FEATURE} — material
 *   {@code legacy_only} em feição nova (§5.4).</li>
 * </ul>
 *
 * <p><b>Divergência registrada (§5.3 × §5.4):</b> para DN incompatível sem
 * peça, o §5.3 manda aviso na {@code JUNCTION} e o §5.4 diz erro. Vale o
 * §5.3 (WARNING), que é a regra específica do elemento; o §5.4 é a tabela
 * geral. A divergência fica registrada aqui e no resumo da frente.
 *
 * <p><b>Feição nova (material legado):</b> a V13_2 não tem coluna de
 * "novidade"; a definição adotada é a origem do dado — feição capturada
 * por método diferente de {@code CAD_IMPORT} não é legado importado, é
 * cadastro novo, e não pode nascer com material {@code legacy_only}
 * (fibrocimento, ferro cinzento e ferro galvanizado do catálogo).
 */
public final class GisValidationEngine {

    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    /** Tipos de nó que são fonte de água (§5.4: captação, poço, reservatório, interligação, elevatória). */
    static final Set<String> SOURCE_TYPE_CODES = Set.of(
            "RESERVOIR", "ELEVATED_TANK", "WELL", "AMAZON_WELL", "SPRING",
            "SURFACE_INTAKE", "FLOATING_INTAKE", "INTAKE",
            "WATER_PUMP_STATION", "PUMP_STATION", "HYD_SOURCE");

    /** Tipos de interface da regra de domínio (§5.3): extravasor e lançamento. */
    static final Set<String> INTERFACE_TYPE_CODES = Set.of("OVERFLOW", "DISCHARGE_POINT");

    /** Funções de arco de interface (§5.3): interligação e extravasor. */
    static final Set<String> INTERFACE_FUNCTIONS = Set.of("INTERCONNECTION", "OVERFLOW_FN");

    /** Funções de adutora na lista {@code network_function} do §4.6 (o "TRANSMISSION" do §5.3). */
    static final Set<String> TRANSMISSION_FUNCTIONS = Set.of("RAW_WATER_MAIN", "TREATED_WATER_MAIN");

    /** PV, TIL e caixas da regra de entrada/saída (§5.3). */
    static final Set<String> MANHOLE_TYPE_CODES = Set.of(
            "MANHOLE", "TIL", "JUNCTION_CHAMBER", "INSPECTION_BOX", "SEWER_CONNECTION_BOX");

    /** Boca de lobo (§5.3): tipos de drenagem; o catálogo de drenagem é de onda posterior. */
    static final Set<String> INLET_TYPE_CODES = Set.of("INLET", "STORM_INLET", "BOCA_DE_LOBO");

    /**
     * Tipos de nó que legitimamente não têm arco: cavalete é ponto final
     * de ramal ({@code gis_connection}) e nó hidráulico vive contido na
     * estrutura ({@code gis_containment}). Todo o resto com papel NODE e
     * nenhum arco é nó solto.
     */
    static final Set<String> ARC_EXEMPT_TYPE_CODES = Set.of("METER_SETTING");

    private GisValidationEngine() {
    }

    /** Entrada completa do motor: retrato da área + regras + nós-fonte por contenção. */
    public record Snapshot(List<NodeFeature> nodes, List<ArcFeature> arcs,
                           List<ConnectionFeature> connections, List<MetaFeature> metas,
                           List<ConnectivityRule> rules, Set<UUID> containedSourceNodeIds,
                           List<ServiceConnectionFeature> serviceConnections) {

        /**
         * Compatibilidade com os chamadores e testes anteriores à
         * GIS-05: sem ligações no retrato, a regra da ligação ativa sem
         * ramal simplesmente não tem o que detectar.
         */
        public Snapshot(List<NodeFeature> nodes, List<ArcFeature> arcs,
                        List<ConnectionFeature> connections, List<MetaFeature> metas,
                        List<ConnectivityRule> rules, Set<UUID> containedSourceNodeIds) {
            this(nodes, arcs, connections, metas, rules, containedSourceNodeIds, List.of());
        }
    }

    public static List<DetectedIssue> detect(Snapshot snapshot) {
        Set<DetectedIssue> issues = new LinkedHashSet<>();
        List<NodeFeature> nodes = snapshot.nodes();
        List<ArcFeature> arcs = snapshot.arcs();
        Map<UUID, NodeFeature> nodeById = new HashMap<>();
        nodes.forEach(n -> nodeById.put(n.id(), n));
        Map<UUID, ArcFeature> arcById = new HashMap<>();
        arcs.forEach(a -> arcById.put(a.id(), a));
        Map<UUID, List<ArcFeature>> arcsByNode = incidentArcs(arcs);

        connectivityCounts(nodes, arcsByNode, snapshot.rules(), issues);
        connectivityConditions(nodes, arcsByNode, snapshot.rules(), issues);
        junctionChanges(nodes, arcsByNode, issues);
        teeBranchDn(nodes, arcsByNode, issues);
        connectionsToTransmission(snapshot.connections(), arcById, issues);
        hydrants(nodes, arcsByNode, nodeById, issues);
        domainMismatch(arcs, nodeById, issues);
        manholeFlow(nodes, arcs, issues);
        inlets(nodes, arcsByNode, nodeById, issues);
        looseNodes(nodes, arcsByNode, snapshot.connections(), snapshot.containedSourceNodeIds(), issues);
        arcsWithoutNode(arcs, issues);
        valves(nodes, issues);
        manholeInvert(nodes, issues);
        connectionsWithoutLink(snapshot.connections(), issues);
        activeConnectionsWithoutServiceLine(snapshot.serviceConnections(), issues);
        islands(nodes, arcs, snapshot.containedSourceNodeIds(), issues);
        gravity(nodes, arcs, issues);
        captureAndMaterial(nodes, arcs, snapshot.connections(), snapshot.metas(), issues);
        return List.copyOf(issues);
    }

    // ------------------------------------------------------------ §5.3 contagem

    private static void connectivityCounts(List<NodeFeature> nodes,
                                           Map<UUID, List<ArcFeature>> arcsByNode,
                                           List<ConnectivityRule> rules,
                                           Set<DetectedIssue> issues) {
        for (ConnectivityRule rule : rules) {
            if (!"CONNECTS".equals(rule.relation())
                    || (rule.minArcs() == null && rule.maxArcs() == null)) {
                continue;
            }
            for (NodeFeature node : nodes) {
                if (!matches(node.typeCode(), node.classCode(), rule.fromTypeCode(), rule.fromClassCode())) {
                    continue;
                }
                long count = arcsByNode.getOrDefault(node.id(), List.of()).stream()
                        .filter(a -> matches(a.typeCode(), a.classCode(), rule.toTypeCode(), rule.toClassCode()))
                        .count();
                if ((rule.minArcs() != null && count < rule.minArcs())
                        || (rule.maxArcs() != null && count > rule.maxArcs())) {
                    issues.add(new DetectedIssue("CONNECTIVITY_ARC_COUNT", rule.severity(),
                            "gis_node", node.id(), null, node.changeSetId()));
                }
            }
        }
    }

    /**
     * Condições do {@code condition} JSONB das regras, no vocabulário da
     * semente de fábrica da frente A: {@code {"dn": "SAME"}},
     * {@code {"dn": "DIFFERENT", "dn_pair": "CATALOG_ITEM"}} e
     * {@code {"on": "DN_OR_MATERIAL_CHANGE"}}. As mesmas verificações existem
     * como checks próprios por tipo (abaixo), porque o aceite pede cada
     * regra do §5.3 com problema gerado mesmo se a semente de fábrica só
     * trouxer as regras de contagem; os códigos são os mesmos e o conjunto
     * deduplica.
     */
    private static void connectivityConditions(List<NodeFeature> nodes,
                                               Map<UUID, List<ArcFeature>> arcsByNode,
                                               List<ConnectivityRule> rules,
                                               Set<DetectedIssue> issues) {
        for (ConnectivityRule rule : rules) {
            if (!"CONNECTS".equals(rule.relation()) || rule.conditionJson() == null
                    || rule.conditionJson().isBlank()) {
                continue;
            }
            JsonNode condition = Json.readTree(rule.conditionJson());
            // Vocabulário da semente de fábrica (R__ da frente A):
            // {"dn": "SAME"} curva/luva; {"dn": "DIFFERENT",
            // "dn_pair": "CATALOG_ITEM"} redução; {"on":
            // "DN_OR_MATERIAL_CHANGE"} junction (linha WARNING própria).
            boolean sameDn = condition.has("dn") && "SAME".equals(condition.get("dn").asString());
            boolean reducerDn = condition.has("dn") && "DIFFERENT".equals(condition.get("dn").asString());
            boolean junctionChange = condition.has("on")
                    && "DN_OR_MATERIAL_CHANGE".equals(condition.get("on").asString());
            if (!sameDn && !reducerDn && !junctionChange) {
                continue;
            }
            for (NodeFeature node : nodes) {
                if (!matches(node.typeCode(), node.classCode(), rule.fromTypeCode(), rule.fromClassCode())) {
                    continue;
                }
                List<ArcFeature> incident = arcsByNode.getOrDefault(node.id(), List.of()).stream()
                        .filter(a -> matches(a.typeCode(), a.classCode(), rule.toTypeCode(), rule.toClassCode()))
                        .toList();
                if (sameDn) {
                    checkSameDn(node, incident, rule.severity(), issues);
                }
                if (reducerDn) {
                    checkReducerDn(node, incident, rule.severity(), issues);
                }
                if (junctionChange) {
                    checkJunctionChange(node, incident, rule.severity(), issues);
                }
            }
        }
    }

    // ------------------------------------------------------------ §5.3 condições por tipo

    /** Curva (BEND) e luva (COUPLING): mesmo DN nos dois arcos; redução: DN e DN2 do item. */
    private static void fittingConditions(List<NodeFeature> nodes,
                                          Map<UUID, List<ArcFeature>> arcsByNode,
                                          Set<DetectedIssue> issues) {
        for (NodeFeature node : nodes) {
            List<ArcFeature> incident = arcsByNode.getOrDefault(node.id(), List.of());
            if ("BEND".equals(node.typeCode()) || "COUPLING".equals(node.typeCode())) {
                checkSameDn(node, incident, ERROR, issues);
            }
            if ("REDUCER".equals(node.typeCode())) {
                checkReducerDn(node, incident, ERROR, issues);
            }
        }
    }

    private static void checkSameDn(NodeFeature node, List<ArcFeature> incident, String severity,
                                    Set<DetectedIssue> issues) {
        if (incident.size() != 2) {
            return; // a contagem errada já é CONNECTIVITY_ARC_COUNT
        }
        Integer dn1 = incident.get(0).dnMm();
        Integer dn2 = incident.get(1).dnMm();
        if (dn1 == null || dn2 == null || !dn1.equals(dn2)) {
            issues.add(new DetectedIssue("CONNECTIVITY_SAME_DN", severity,
                    "gis_node", node.id(), null, node.changeSetId()));
        }
    }

    private static void checkReducerDn(NodeFeature node, List<ArcFeature> incident, String severity,
                                       Set<DetectedIssue> issues) {
        if (incident.size() != 2 || node.dnMm() == null || node.dn2Mm() == null) {
            if (incident.size() == 2) {
                issues.add(new DetectedIssue("CONNECTIVITY_REDUCER_DN", severity,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
            return;
        }
        Set<Integer> arcDns = new HashSet<>();
        incident.forEach(a -> arcDns.add(a.dnMm()));
        if (!arcDns.equals(Set.of(node.dnMm(), node.dn2Mm()))) {
            issues.add(new DetectedIssue("CONNECTIVITY_REDUCER_DN", severity,
                    "gis_node", node.id(), null, node.changeSetId()));
        }
    }

    /** JUNCTION com mudança de DN ou de material sem peça: WARNING (§5.3; ver divergência no cabeçalho). */
    private static void junctionChanges(List<NodeFeature> nodes,
                                        Map<UUID, List<ArcFeature>> arcsByNode,
                                        Set<DetectedIssue> issues) {
        fittingConditions(nodes, arcsByNode, issues);
        for (NodeFeature node : nodes) {
            if (!"JUNCTION".equals(node.typeCode())) {
                continue;
            }
            checkJunctionChange(node, arcsByNode.getOrDefault(node.id(), List.of()), WARNING, issues);
        }
    }

    private static void checkJunctionChange(NodeFeature node, List<ArcFeature> incident,
                                            String severity, Set<DetectedIssue> issues) {
        Set<Integer> dns = new HashSet<>();
        Set<String> materials = new HashSet<>();
        for (ArcFeature arc : incident) {
            dns.add(arc.dnMm());
            materials.add(arc.materialCode());
        }
        dns.remove(null);
        materials.remove(null);
        if (dns.size() > 1 || materials.size() > 1) {
            issues.add(new DetectedIssue("DN_MISMATCH_WITHOUT_FITTING", severity,
                    "gis_node", node.id(), null, node.changeSetId()));
        }
    }

    /**
     * Tê (TEE) com 3 arcos (correção I3 da revisão): o arco-ramal é o de DN
     * diferente dos dois passantes — identificável só quando exatamente
     * dois arcos têm o mesmo DN e o terceiro difere. O ramal tem de ter o
     * {@code dn2_mm} do item de catálogo do tê e os passantes, o
     * {@code dn_mm}. Se o item do tê não tiver {@code dn2_mm}, o check não
     * dispara (sem dado, sem problema); idem quando o ramal não é
     * identificável (os 3 DNs iguais, todos diferentes ou algum DN de arco
     * ausente) ou quando falta o {@code dn_mm} do item para conferir os
     * passantes (essa metade simplesmente não é conferida).
     */
    private static void teeBranchDn(List<NodeFeature> nodes,
                                    Map<UUID, List<ArcFeature>> arcsByNode,
                                    Set<DetectedIssue> issues) {
        for (NodeFeature node : nodes) {
            if (!"TEE".equals(node.typeCode()) || node.dn2Mm() == null) {
                continue;
            }
            List<ArcFeature> incident = arcsByNode.getOrDefault(node.id(), List.of());
            if (incident.size() != 3
                    || incident.stream().anyMatch(a -> a.dnMm() == null)) {
                continue;
            }
            Integer d0 = incident.get(0).dnMm();
            Integer d1 = incident.get(1).dnMm();
            Integer d2 = incident.get(2).dnMm();
            Integer throughDn;
            Integer branchDn;
            if (d0.equals(d1) && !d0.equals(d2)) {
                throughDn = d0;
                branchDn = d2;
            } else if (d0.equals(d2) && !d0.equals(d1)) {
                throughDn = d0;
                branchDn = d1;
            } else if (d1.equals(d2) && !d1.equals(d0)) {
                throughDn = d1;
                branchDn = d0;
            } else {
                continue; // ramal nao identificavel: sem dado conclusivo, sem problema
            }
            boolean branchWrong = !branchDn.equals(node.dn2Mm());
            boolean throughWrong = node.dnMm() != null && !throughDn.equals(node.dnMm());
            if (branchWrong || throughWrong) {
                issues.add(new DetectedIssue("TEE_BRANCH_DN_MISMATCH", ERROR,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    // ------------------------------------------------------------ §5.3 não-contagem

    private static void connectionsToTransmission(List<ConnectionFeature> connections,
                                                  Map<UUID, ArcFeature> arcById,
                                                  Set<DetectedIssue> issues) {
        for (ConnectionFeature connection : connections) {
            if (!isActive(connection.lifecycleStatus()) || connection.arcId() == null) {
                continue;
            }
            ArcFeature arc = arcById.get(connection.arcId());
            if (arc == null || arc.networkFunction() == null) {
                continue;
            }
            if (TRANSMISSION_FUNCTIONS.contains(arc.networkFunction())) {
                issues.add(new DetectedIssue("CONNECTION_TO_TRANSMISSION", ERROR,
                        "gis_connection", connection.id(), arc.id(), connection.changeSetId()));
            } else if ("SUBMAIN".equals(arc.networkFunction())) {
                issues.add(new DetectedIssue("CONNECTION_TO_TRANSMISSION", WARNING,
                        "gis_connection", connection.id(), arc.id(), connection.changeSetId()));
            }
        }
    }

    private static void hydrants(List<NodeFeature> nodes,
                                 Map<UUID, List<ArcFeature>> arcsByNode,
                                 Map<UUID, NodeFeature> nodeById,
                                 Set<DetectedIssue> issues) {
        for (NodeFeature node : nodes) {
            if (!"HYDRANT".equals(node.classCode())
                    && (node.typeCode() == null || !node.typeCode().startsWith("HYDRANT"))) {
                continue;
            }
            // Ligado por ramal de hidrante (arco HYDRANT_LATERAL ou função
            // HYDRANT_BRANCH) ou por tê: arco cuja outra ponta é um TEE.
            boolean connected = arcsByNode.getOrDefault(node.id(), List.of()).stream().anyMatch(a -> {
                if ("HYDRANT_LATERAL".equals(a.typeCode()) || "HYDRANT_BRANCH".equals(a.networkFunction())) {
                    return true;
                }
                UUID other = node.id().equals(a.node1Id()) ? a.node2Id() : a.node1Id();
                NodeFeature otherNode = other == null ? null : nodeById.get(other);
                return otherNode != null && "TEE".equals(otherNode.typeCode());
            });
            if (!connected) {
                issues.add(new DetectedIssue("HYDRANT_NOT_CONNECTED", ERROR,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    private static void domainMismatch(List<ArcFeature> arcs, Map<UUID, NodeFeature> nodeById,
                                       Set<DetectedIssue> issues) {
        for (ArcFeature arc : arcs) {
            if (arc.domain() == null || INTERFACE_FUNCTIONS.contains(arc.networkFunction())
                    || INTERFACE_TYPE_CODES.contains(arc.typeCode())) {
                continue;
            }
            for (UUID nodeId : new UUID[]{arc.node1Id(), arc.node2Id()}) {
                if (nodeId == null) {
                    continue;
                }
                NodeFeature node = nodeById.get(nodeId);
                if (node == null || node.domain() == null
                        || INTERFACE_TYPE_CODES.contains(node.typeCode())) {
                    continue;
                }
                if (!arc.domain().equals(node.domain())) {
                    issues.add(new DetectedIssue("DOMAIN_MISMATCH", ERROR,
                            "gis_arc", arc.id(), node.id(), arc.changeSetId()));
                }
            }
        }
    }

    /**
     * Entrada e saída de PV/TIL/caixa pelo sentido do fluxo. Sentido do
     * arco (§5.6): {@code node_1 → node_2}, invertido com
     * {@code AGAINST_GEOMETRY}; {@code BOTH} conta dos dois lados (não dá
     * para provar ausência). Exceção configurável por tipo (§5.3):
     * {@code terminal_config} do tipo com {@code "flow_exception": true}
     * dispensa a regra para o tipo.
     */
    private static void manholeFlow(List<NodeFeature> nodes, List<ArcFeature> arcs,
                                    Set<DetectedIssue> issues) {
        Map<UUID, int[]> flow = new HashMap<>(); // nó -> [entradas, saídas]
        for (ArcFeature arc : arcs) {
            if (arc.node1Id() == null || arc.node2Id() == null) {
                continue;
            }
            UUID from = "AGAINST_GEOMETRY".equals(arc.flowDirection()) ? arc.node2Id() : arc.node1Id();
            UUID to = "AGAINST_GEOMETRY".equals(arc.flowDirection()) ? arc.node1Id() : arc.node2Id();
            flow.computeIfAbsent(to, k -> new int[2])[0]++;
            flow.computeIfAbsent(from, k -> new int[2])[1]++;
            if ("BOTH".equals(arc.flowDirection())) {
                flow.computeIfAbsent(from, k -> new int[2])[0]++;
                flow.computeIfAbsent(to, k -> new int[2])[1]++;
            }
        }
        for (NodeFeature node : nodes) {
            if (!MANHOLE_TYPE_CODES.contains(node.typeCode()) || flowException(node)) {
                continue;
            }
            int[] counts = flow.getOrDefault(node.id(), new int[2]);
            if (counts[0] == 0 || counts[1] == 0) {
                issues.add(new DetectedIssue("MANHOLE_FLOW", ERROR,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    private static boolean flowException(NodeFeature node) {
        if (node.terminalConfig() == null || node.terminalConfig().isBlank()) {
            return false;
        }
        JsonNode config = Json.readTree(node.terminalConfig());
        return config.has("flow_exception") && config.get("flow_exception").asBoolean(false);
    }

    private static void inlets(List<NodeFeature> nodes, Map<UUID, List<ArcFeature>> arcsByNode,
                               Map<UUID, NodeFeature> nodeById, Set<DetectedIssue> issues) {
        for (NodeFeature node : nodes) {
            if (!INLET_TYPE_CODES.contains(node.typeCode())) {
                continue;
            }
            boolean connected = arcsByNode.getOrDefault(node.id(), List.of()).stream().anyMatch(a -> {
                UUID other = node.id().equals(a.node1Id()) ? a.node2Id() : a.node1Id();
                NodeFeature target = other == null ? null : nodeById.get(other);
                return target != null && (MANHOLE_TYPE_CODES.contains(target.typeCode())
                        || "GALLERY".equals(target.typeCode()) || "GALLERY".equals(a.typeCode()));
            });
            if (!connected) {
                issues.add(new DetectedIssue("INLET_NOT_CONNECTED", ERROR,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    // ------------------------------------------------------------ §5.4

    private static void looseNodes(List<NodeFeature> nodes, Map<UUID, List<ArcFeature>> arcsByNode,
                                   List<ConnectionFeature> connections, Set<UUID> containedNodeIds,
                                   Set<DetectedIssue> issues) {
        Set<UUID> tapNodes = new HashSet<>();
        connections.forEach(c -> {
            if (c.tapNodeId() != null) {
                tapNodes.add(c.tapNodeId());
            }
        });
        for (NodeFeature node : nodes) {
            if (!isActive(node.lifecycleStatus()) || !"NODE".equals(node.topologyRole())) {
                continue;
            }
            if (ARC_EXEMPT_TYPE_CODES.contains(node.typeCode())
                    || containedNodeIds.contains(node.id()) || tapNodes.contains(node.id())) {
                continue;
            }
            if (arcsByNode.getOrDefault(node.id(), List.of()).isEmpty()) {
                issues.add(new DetectedIssue("NODE_WITHOUT_ARC", ERROR,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    private static void arcsWithoutNode(List<ArcFeature> arcs, Set<DetectedIssue> issues) {
        for (ArcFeature arc : arcs) {
            if ((arc.node1Id() == null || arc.node2Id() == null)
                    && ("IN_SERVICE".equals(arc.lifecycleStatus())
                    || "UNDER_CONSTRUCTION".equals(arc.lifecycleStatus()))) {
                issues.add(new DetectedIssue("ARC_WITHOUT_NODE", ERROR,
                        "gis_arc", arc.id(), null, arc.changeSetId()));
            }
        }
    }

    private static void valves(List<NodeFeature> nodes, Set<DetectedIssue> issues) {
        for (NodeFeature node : nodes) {
            if (!"VALVE".equals(node.classCode())) {
                continue;
            }
            if (node.normalStatus() == null || node.normalStatus().isBlank()
                    || node.operationalStatus() == null || node.operationalStatus().isBlank()) {
                issues.add(new DetectedIssue("VALVE_WITHOUT_STATE", WARNING,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    private static void manholeInvert(List<NodeFeature> nodes, Set<DetectedIssue> issues) {
        for (NodeFeature node : nodes) {
            if (MANHOLE_TYPE_CODES.contains(node.typeCode()) && node.invertElevationM() == null) {
                issues.add(new DetectedIssue("MANHOLE_WITHOUT_INVERT", WARNING,
                        "gis_node", node.id(), null, node.changeSetId()));
            }
        }
    }

    private static void activeConnectionsWithoutServiceLine(
            List<ServiceConnectionFeature> serviceConnections, Set<DetectedIssue> issues) {
        if (serviceConnections == null) {
            return;
        }
        for (ServiceConnectionFeature sc : serviceConnections) {
            if (sc.open() && !"NO_CONNECTION".equals(sc.systemRole()) && sc.serviceLineCount() == 0) {
                issues.add(new DetectedIssue("ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE", WARNING,
                        "service_connections", sc.id(), null, null));
            }
        }
    }

    private static void connectionsWithoutLink(List<ConnectionFeature> connections,
                                               Set<DetectedIssue> issues) {
        for (ConnectionFeature connection : connections) {
            if (isActive(connection.lifecycleStatus()) && connection.serviceConnectionId() == null) {
                issues.add(new DetectedIssue("CONNECTION_WITHOUT_LINK", WARNING,
                        "gis_connection", connection.id(), null, connection.changeSetId()));
            }
        }
    }

    /**
     * Ilha sem fonte: componentes sobre os arcos de água; componente sem
     * nenhum nó-fonte (tipo de fonte, estrutura-fonte contida ou arco de
     * interligação) gera um problema ancorado no representante do
     * componente (menor UUID, o mesmo do {@link GisGraph}).
     */
    private static void islands(List<NodeFeature> nodes, List<ArcFeature> arcs,
                                Set<UUID> containedSourceNodeIds, Set<DetectedIssue> issues) {
        List<ArcFeature> waterArcs = arcs.stream().filter(a -> "WATER".equals(a.domain())).toList();
        List<GisGraph.Edge> edges = waterArcs.stream()
                .filter(a -> a.node1Id() != null && a.node2Id() != null)
                .map(a -> new GisGraph.Edge(a.id(), a.node1Id(), a.node2Id()))
                .toList();
        Set<UUID> memberNodes = new HashSet<>();
        edges.forEach(e -> {
            memberNodes.add(e.node1Id());
            memberNodes.add(e.node2Id());
        });
        Map<UUID, NodeFeature> nodeById = new HashMap<>();
        nodes.forEach(n -> nodeById.put(n.id(), n));
        Set<UUID> interconnectionNodes = new HashSet<>();
        waterArcs.stream().filter(a -> "INTERCONNECTION".equals(a.networkFunction())).forEach(a -> {
            if (a.node1Id() != null) {
                interconnectionNodes.add(a.node1Id());
            }
            if (a.node2Id() != null) {
                interconnectionNodes.add(a.node2Id());
            }
        });
        Map<UUID, UUID> components = GisGraph.components(memberNodes, edges);
        for (Map.Entry<UUID, List<UUID>> group : GisGraph.groups(components).entrySet()) {
            boolean hasSource = group.getValue().stream().anyMatch(nodeId -> {
                NodeFeature node = nodeById.get(nodeId);
                return containedSourceNodeIds.contains(nodeId) || interconnectionNodes.contains(nodeId)
                        || (node != null && SOURCE_TYPE_CODES.contains(node.typeCode()));
            });
            if (!hasSource) {
                NodeFeature anchor = nodeById.get(group.getKey());
                issues.add(new DetectedIssue("ISLAND_WITHOUT_SOURCE", WARNING,
                        "gis_node", group.getKey(), null,
                        anchor == null ? null : anchor.changeSetId()));
            }
        }
    }

    /** Declividade negativa e ciclo dirigido nas redes por gravidade (esgoto e drenagem, não pressurizadas). */
    private static void gravity(List<NodeFeature> nodes, List<ArcFeature> arcs,
                                Set<DetectedIssue> issues) {
        List<GisGraph.DirectedEdge> directed = new ArrayList<>();
        Map<UUID, ArcFeature> arcById = new HashMap<>();
        for (ArcFeature arc : arcs) {
            arcById.put(arc.id(), arc);
            boolean gravityDomain = "SEWER".equals(arc.domain()) || "STORM".equals(arc.domain());
            if (!gravityDomain || Boolean.TRUE.equals(arc.pressurized())) {
                continue;
            }
            // upstream/downstream são relativos ao fluxo (§3.6.2): fundo de
            // jusante acima do de montante é declividade negativa.
            if (arc.upstreamInvertM() != null && arc.downstreamInvertM() != null
                    && arc.downstreamInvertM().compareTo(arc.upstreamInvertM()) > 0) {
                issues.add(new DetectedIssue("NEGATIVE_SLOPE", ERROR,
                        "gis_arc", arc.id(), null, arc.changeSetId()));
            }
            if (arc.node1Id() != null && arc.node2Id() != null) {
                boolean against = "AGAINST_GEOMETRY".equals(arc.flowDirection());
                directed.add(new GisGraph.DirectedEdge(arc.id(),
                        against ? arc.node2Id() : arc.node1Id(),
                        against ? arc.node1Id() : arc.node2Id()));
            }
        }
        List<UUID> cycle = GisGraph.findDirectedCycle(directed);
        if (!cycle.isEmpty()) {
            ArcFeature first = arcById.get(cycle.get(0));
            issues.add(new DetectedIssue("GRAVITY_CYCLE", ERROR,
                    "gis_arc", cycle.get(0), null, first == null ? null : first.changeSetId()));
        }
    }

    private static void captureAndMaterial(List<NodeFeature> nodes, List<ArcFeature> arcs,
                                           List<ConnectionFeature> connections, List<MetaFeature> metas,
                                           Set<DetectedIssue> issues) {
        nodes.forEach(n -> checkMeta("gis_node", n.id(), n.captureMethod(), n.accuracyStatus(),
                n.legacyOnly(), n.changeSetId(), issues));
        arcs.forEach(a -> checkMeta("gis_arc", a.id(), a.captureMethod(), a.accuracyStatus(),
                a.legacyOnly(), a.changeSetId(), issues));
        connections.forEach(c -> checkMeta("gis_connection", c.id(), c.captureMethod(), c.accuracyStatus(),
                c.legacyOnly(), c.changeSetId(), issues));
        metas.forEach(m -> checkMeta(m.featureTable(), m.id(), m.captureMethod(), m.accuracyStatus(),
                m.legacyOnly(), m.changeSetId(), issues));
    }

    private static void checkMeta(String table, UUID id, String captureMethod, String accuracyStatus,
                                  boolean legacyOnly, UUID changeSetId, Set<DetectedIssue> issues) {
        if (captureMethod == null || captureMethod.isBlank()) {
            issues.add(new DetectedIssue("MISSING_CAPTURE_METADATA", ERROR, table, id, null, changeSetId));
        }
        if ("OUT_OF_TARGET".equals(accuracyStatus)) {
            issues.add(new DetectedIssue("ACCURACY_OUT_OF_TARGET", WARNING, table, id, null, changeSetId));
        }
        // Feição nova = captura que não é importação de legado (ver cabeçalho).
        if (legacyOnly && captureMethod != null && !"CAD_IMPORT".equals(captureMethod)) {
            issues.add(new DetectedIssue("LEGACY_MATERIAL_ON_NEW_FEATURE", ERROR, table, id, null, changeSetId));
        }
    }

    // ------------------------------------------------------------ apoio

    private static Map<UUID, List<ArcFeature>> incidentArcs(List<ArcFeature> arcs) {
        Map<UUID, List<ArcFeature>> byNode = new HashMap<>();
        for (ArcFeature arc : arcs) {
            if (arc.node1Id() != null) {
                byNode.computeIfAbsent(arc.node1Id(), k -> new ArrayList<>()).add(arc);
            }
            if (arc.node2Id() != null && !Objects.equals(arc.node1Id(), arc.node2Id())) {
                byNode.computeIfAbsent(arc.node2Id(), k -> new ArrayList<>()).add(arc);
            }
        }
        return byNode;
    }

    /** Casa tipo/classe da feição com o lado da regra; lado nulo da regra casa tudo. */
    private static boolean matches(String typeCode, String classCode, String ruleTypeCode, String ruleClassCode) {
        if (ruleTypeCode != null) {
            return ruleTypeCode.equals(typeCode);
        }
        if (ruleClassCode != null) {
            return ruleClassCode.equals(classCode);
        }
        return true;
    }

    private static boolean isActive(String lifecycleStatus) {
        return !"ABANDONED".equals(lifecycleStatus) && !"PLANNED".equals(lifecycleStatus);
    }
}
