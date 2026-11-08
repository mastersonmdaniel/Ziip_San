package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValveState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Setores de manobra PRÉ-CALCULADOS (GIS-11): a partição da rede em
 * operação obtida removendo os nós das válvulas OPERÁVEIS — cada
 * componente restante é um setor, delimitado pelas válvulas que o
 * cercam (as suas fronteiras). Válvula inoperável NÃO delimita setor
 * (não fecha: atravessá-la é o comportamento do isolamento).
 *
 * <p>O pré-cálculo é determinístico: setores ordenados pelo menor
 * código de nó e nomeados {@code MS-01}, {@code MS-02}… na ordem —
 * o mesmo retrato gera os mesmos setores, e a persistência é da
 * tabela {@code gis_maneuver_sector} do SQL pendente (a rotina de
 * recálculo é disparada pela fiação quando a GIS-03 marca a área
 * suja; aqui fica o cálculo puro que a CI confere).
 */
public final class ManeuverSectors {

    public record ManeuverSector(String sectorCode, List<UUID> nodeIds, List<UUID> arcIds,
                                 List<UUID> boundaryValveNodeIds,
                                 List<UUID> connectionIds, int totalEconomies,
                                 boolean hasSource) {
        public ManeuverSector {
            nodeIds = List.copyOf(nodeIds);
            arcIds = List.copyOf(arcIds);
            boundaryValveNodeIds = List.copyOf(boundaryValveNodeIds);
            connectionIds = List.copyOf(connectionIds);
        }
    }

    private ManeuverSectors() {
    }

    public static List<ManeuverSector> precompute(NetworkSnapshot snapshot) {
        var nodesById = snapshot.nodesById();
        var valvesByNode = snapshot.valvesByNode();
        Set<UUID> barred = new HashSet<>();
        snapshot.valves().stream().filter(ValveState::operable).forEach(v -> barred.add(v.nodeId()));

        ManeuverGraph graph = new ManeuverGraph(snapshot);
        Set<UUID> unassigned = newLinkedHashSetOrdered(snapshot);
        List<List<UUID>> components = new ArrayList<>();
        while (!unassigned.isEmpty()) {
            UUID seed = unassigned.iterator().next();
            Set<UUID> comp = graph.componentWithout(seed, barred);
            components.add(new ArrayList<>(comp));
            unassigned.removeAll(comp);
        }
        components.sort(Comparator.comparing(
                (List<UUID> c) -> c.stream().map(id -> nodesById.get(id).code()).min(String::compareTo).orElse("")));

        List<ManeuverSector> out = new ArrayList<>();
        int seq = 1;
        for (List<UUID> comp : components) {
            Set<UUID> compSet = new HashSet<>(comp);
            List<UUID> arcs = snapshot.serviceArcs().stream()
                    .filter(a -> compSet.contains(a.node1Id()) && compSet.contains(a.node2Id()))
                    .map(a -> a.id()).sorted(Comparator.comparing(id -> snapshot.arcsById().get(id).code())).toList();
            Set<UUID> boundary = new HashSet<>();
            for (UUID n : comp) {
                for (var a : graph.arcsAt(n)) {
                    UUID other = a.node1Id().equals(n) ? a.node2Id() : a.node1Id();
                    if (barred.contains(other)) boundary.add(other);
                }
            }
            List<FunctionalConnection> conns = snapshot.connections().stream()
                    .filter(c -> (c.tapNodeId() != null && compSet.contains(c.tapNodeId()))
                            || (c.arcId() != null && arcs.contains(c.arcId())))
                    .toList();
            List<UUID> nodeOrder = new ArrayList<>(comp);
            nodeOrder.sort(Comparator.comparing(id -> nodesById.get(id).code()));
            List<UUID> boundaryOrder = new ArrayList<>(boundary);
            boundaryOrder.sort(Comparator.comparing(id -> nodesById.get(id).code()));
            out.add(new ManeuverSector(String.format("MS-%02d", seq++), nodeOrder, arcs, boundaryOrder,
                    conns.stream().map(FunctionalConnection::id).toList(),
                    conns.stream().mapToInt(FunctionalConnection::economies).sum(),
                    comp.stream().anyMatch(id -> nodesById.get(id).source())));
        }
        return List.copyOf(out);
    }

    /** Setor que contém o nó dado (nulo se o nó é válvula operável ou está fora). */
    public static ManeuverSector sectorOf(List<ManeuverSector> sectors, UUID nodeId) {
        return sectors.stream().filter(s -> s.nodeIds().contains(nodeId)).findFirst().orElse(null);
    }

    private static Set<UUID> newLinkedHashSetOrdered(NetworkSnapshot snapshot) {
        Set<UUID> s = new java.util.LinkedHashSet<>();
        snapshot.nodes().stream()
                .filter(n -> !(snapshot.valvesByNode().containsKey(n.id())
                        && snapshot.valvesByNode().get(n.id()).operable()))
                .sorted(Comparator.comparing(br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkNode::code))
                .forEach(n -> s.add(n.id()));
        // Só entram nós com pelo menos um arco em operação: nó sem
        // arco em operação NÃO forma setor de manobra (decisão nº 4
        // do relatório da ED-54), TENHA OU NÃO ligação funcional —
        // a ligação não resgata o nó (correção D6 da revisão: o
        // comentário anterior prometia manter "isolados com
        // ligação", o que o código nunca fez).
        Set<UUID> withArc = new HashSet<>();
        snapshot.serviceArcs().forEach(a -> { withArc.add(a.node1Id()); withArc.add(a.node2Id()); });
        s.removeIf(id -> !withArc.contains(id));
        return s;
    }
}
