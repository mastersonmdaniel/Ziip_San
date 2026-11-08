package br.com.ziipsan.intelligence.gis.maneuver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Retrato da topologia persistida (GIS-03/P2.21: {@code gis_node},
 * {@code gis_arc}, {@code gis_connection}) resolvido para a GIS-11
 * (ED-54): traçados, isolamento e manobras. Registros puros, sem
 * banco e sem Spring — quem lê o banco é a fiação (porta
 * {@link ManeuverPorts.SnapshotPort}); quem decide é o motor
 * ({@link IsolationEngine}, {@link ManeuverGraph},
 * {@link ManeuverSectors}).
 *
 * <p>Convenções fiéis ao modelo vigente:
 * <ul>
 *   <li>sentido do arco = {@code flow_direction} da V13_2:
 *       {@code WITH_GEOMETRY} (nó 1 → nó 2), {@code AGAINST_GEOMETRY}
 *       (nó 2 → nó 1), {@code BOTH} (bidirecional), {@code UNKNOWN}
 *       (sem sentido declarado — o traçado dirigido recusa presumir:
 *       o arco entra no grafo NÃO dirigido do isolamento, mas não
 *       gera aresta dirigida de montante/jusante);</li>
 *   <li>válvula é NÓ de classe {@code VALVE} (GIS-03, regra
 *       {@code VALVE_WITHOUT_STATE}): estado normal/operacional vêm
 *       de {@code gis_node.normal_status}/{@code operational_status};
 *       aqui o estado corrente é {@link ValveState} e a operabilidade
 *       é fato de campo/exercício (a ED-56 mantém o programa de
 *       exercício — OPE-09 — e alimenta este retrato);</li>
 *   <li>ligação funcional (SG-852) é a {@code gis_connection} da
 *       GIS-03 ligada à ligação da GIS-05/P2.23
 *       ({@code service_connection_id} da V13_10): imóvel, unidades
 *       consumidoras e economias vêm dela — nenhum vínculo novo é
 *       inventado aqui;</li>
 *   <li>unidade essencial usa os tipos da P1.3/ED-35
 *       ({@code HOSPITAL}, {@code SCHOOL}, {@code COLLECTIVE_CARE},
 *       de {@code unit_essential_users}, V3_12); cliente sensível é
 *       marcação do cadastro (sensível ≠ essencial: o sensível ordena
 *       o aviso primeiro; o essencial exige fornecimento de
 *       emergência na ED-35).</li>
 * </ul>
 *
 * <p>Coordenadas {@code x}/{@code y} são as do retrato (projeção do
 * tenant); servem só aos polígonos de influência/roteamento
 * ({@link InfluenceBasinService}) — nenhuma distância de rede é
 * derivada delas.
 */
public final class ManeuverModel {

    private ManeuverModel() {
    }

    /** Sentido de fluxo declarado do arco (espelho de {@code flow_direction}). */
    public enum FlowDirection { WITH_GEOMETRY, AGAINST_GEOMETRY, BOTH, UNKNOWN }

    /** Estado corrente de uma válvula. */
    public enum ValvePosition { OPEN, CLOSED }

    /** Nó da rede persistida. {@code source} = fonte de abastecimento do componente. */
    public record NetworkNode(UUID id, String code, String classCode, boolean source,
                              Double x, Double y) {
        public NetworkNode {
            Objects.requireNonNull(id, "no sem id");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("no sem codigo");
        }
    }

    /** Arco da rede persistida, com as pontas resolvidas da GIS-03 (snap da V13_8). */
    public record NetworkArc(UUID id, String code, UUID node1Id, UUID node2Id,
                             FlowDirection flowDirection, String lifecycleStatus) {
        public NetworkArc {
            Objects.requireNonNull(id, "arco sem id");
            Objects.requireNonNull(node1Id, "arco sem no 1 (GIS-03: ARC_WITHOUT_NODE)");
            Objects.requireNonNull(node2Id, "arco sem no 2 (GIS-03: ARC_WITHOUT_NODE)");
            if (node1Id.equals(node2Id)) throw new IllegalArgumentException("arco em laco sobre o mesmo no: " + code);
            Objects.requireNonNull(flowDirection, "arco sem flow_direction");
        }

        /** Em operação (só arco IN_SERVICE entra em traçado, setor e isolamento). */
        public boolean inService() {
            return lifecycleStatus == null || "IN_SERVICE".equals(lifecycleStatus);
        }
    }

    /**
     * Válvula (nó de classe VALVE) com operabilidade e estado corrente.
     * {@code operable=false} é a válvula inoperável do aceite: ela NÃO
     * fecha — o isolamento a ATRAVESSA e a reporta nomeadamente.
     */
    public record ValveState(UUID nodeId, String code, boolean operable, ValvePosition position) {
        public ValveState {
            Objects.requireNonNull(nodeId, "valvula sem no");
            Objects.requireNonNull(position, "valvula sem estado corrente");
        }
    }

    /**
     * Conexão funcional (SG-852): ramal ({@code gis_connection}) que
     * liga um arco/nó da rede à ligação da GIS-05 e, por ela, ao
     * imóvel e às suas unidades consumidoras/economias.
     * {@code essentialKind} nulo = unidade não essencial;
     * {@code sensitive} = cliente sensível do cadastro.
     */
    public record FunctionalConnection(UUID id, String code, UUID arcId, UUID tapNodeId,
                                       UUID serviceConnectionId, UUID propertyId,
                                       List<UUID> consumerUnitIds, int economies,
                                       boolean sensitive, String essentialKind) {
        public FunctionalConnection {
            Objects.requireNonNull(id, "conexao funcional sem id");
            if (arcId == null && tapNodeId == null)
                throw new IllegalArgumentException("conexao funcional sem arco nem no de derivacao (SG-852)");
            Objects.requireNonNull(serviceConnectionId, "conexao funcional sem ligacao da GIS-05 (SG-852)");
            Objects.requireNonNull(propertyId, "conexao funcional sem imovel (SG-852)");
            consumerUnitIds = consumerUnitIds == null ? List.of() : List.copyOf(consumerUnitIds);
            if (economies < 0) throw new IllegalArgumentException("economias negativas");
            if (essentialKind != null
                    && !List.of("HOSPITAL", "SCHOOL", "COLLECTIVE_CARE").contains(essentialKind))
                throw new IllegalArgumentException("tipo de unidade essencial desconhecido: " + essentialKind);
        }

        public boolean essential() { return essentialKind != null; }
    }

    /** Retrato completo e validado da rede para um cálculo da GIS-11. */
    public record NetworkSnapshot(List<NetworkNode> nodes, List<NetworkArc> arcs,
                                  List<ValveState> valves,
                                  List<FunctionalConnection> connections) {
        public NetworkSnapshot {
            nodes = List.copyOf(nodes);
            arcs = List.copyOf(arcs);
            valves = List.copyOf(valves);
            connections = List.copyOf(connections);
            Set<UUID> nodeIds = new HashSet<>();
            Map<UUID, NetworkNode> byId = new HashMap<>();
            for (NetworkNode n : nodes) {
                if (!nodeIds.add(n.id())) throw new IllegalArgumentException("no repetido no retrato: " + n.code());
                byId.put(n.id(), n);
            }
            Set<UUID> arcIds = new HashSet<>();
            for (NetworkArc a : arcs) {
                if (!arcIds.add(a.id())) throw new IllegalArgumentException("arco repetido no retrato: " + a.code());
                if (!nodeIds.contains(a.node1Id()) || !nodeIds.contains(a.node2Id()))
                    throw new IllegalArgumentException("arco com ponta fora do retrato: " + a.code());
            }
            Set<UUID> valveNodes = new HashSet<>();
            for (ValveState v : valves) {
                NetworkNode n = byId.get(v.nodeId());
                if (n == null) throw new IllegalArgumentException("valvula sem no no retrato: " + v.code());
                if (!"VALVE".equals(n.classCode()))
                    throw new IllegalArgumentException("valvula sobre no que nao e de classe VALVE: " + v.code());
                if (!valveNodes.add(v.nodeId())) throw new IllegalArgumentException("valvula repetida: " + v.code());
            }
            for (FunctionalConnection c : connections) {
                if (c.arcId() != null && !arcIds.contains(c.arcId()))
                    throw new IllegalArgumentException("conexao funcional com arco fora do retrato: " + c.code());
                if (c.tapNodeId() != null && !nodeIds.contains(c.tapNodeId()))
                    throw new IllegalArgumentException("conexao funcional com no fora do retrato: " + c.code());
            }
        }

        public Map<UUID, NetworkNode> nodesById() {
            Map<UUID, NetworkNode> m = new HashMap<>();
            nodes.forEach(n -> m.put(n.id(), n));
            return m;
        }

        public Map<UUID, NetworkArc> arcsById() {
            Map<UUID, NetworkArc> m = new HashMap<>();
            arcs.forEach(a -> m.put(a.id(), a));
            return m;
        }

        public Map<UUID, ValveState> valvesByNode() {
            Map<UUID, ValveState> m = new HashMap<>();
            valves.forEach(v -> m.put(v.nodeId(), v));
            return m;
        }

        /** Arcos em operação (os demais ficam fora de qualquer cálculo). */
        public List<NetworkArc> serviceArcs() {
            return arcs.stream().filter(NetworkArc::inService).toList();
        }
    }

    /** Alvo de um traçado/isolamento: um arco OU um nó (nunca os dois, nunca válvula). */
    public record Target(UUID arcId, UUID nodeId) {
        public Target {
            if ((arcId == null) == (nodeId == null))
                throw new IllegalArgumentException("alvo exige exatamente um arco ou um no");
        }

        public static Target arc(UUID arcId) { return new Target(arcId, null); }

        public static Target node(UUID nodeId) { return new Target(null, nodeId); }
    }
}
