package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkNode;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Áreas de influência, bacias e polígonos de roteamento
 * (SG-887, SG-889):
 *
 * <ul>
 *   <li><b>bacia</b> de um nó/arco = o conjunto a MONTANTE dele
 *       (quem o alimenta); <b>área de influência</b> = o conjunto a
 *       JUSANTE (quem ele afeta) — ambos pelo traçado dirigido da
 *       GIS-11, não por distância euclidiana;</li>
 *   <li><b>bacias por exutório/fonte</b>: partição determinística em
 *       que cada nó pertence à bacia da PRIMEIRA fonte alcançável a
 *       montante (desempate pelo menor código de fonte); nó sem
 *       fonte a montante fica na bacia {@code SEM_FONTE} — visível,
 *       nunca atribuído por chute;</li>
 *   <li><b>polígono de roteamento</b> = envolvente convexa (convex
 *       hull, Andrew monotone chain) das coordenadas dos nós do
 *       conjunto — é o polígono que delimita a área de trabalho para
 *       a equipe e para o mapa. NÃO é rota de deslocamento: o solver
 *       de equipes (pgRouting) segue bloqueado pelo O8-1/ADR; o
 *       polígono é geometria da ÁREA, não caminho.</li>
 * </ul>
 *
 * <p>Sem coordenada no retrato, o polígono volta vazio e o fato é
 * declarado no resultado ({@code polygonAvailable=false}) — nenhuma
 * geometria é inventada.
 */
public final class InfluenceBasinService {

    public record Point(double x, double y) implements Comparable<Point> {
        @Override public int compareTo(Point o) {
            int c = Double.compare(x, o.x);
            return c != 0 ? c : Double.compare(y, o.y);
        }
    }

    public record AreaResult(List<UUID> nodeIds, List<Point> routingPolygon, boolean polygonAvailable) {
        public AreaResult {
            nodeIds = List.copyOf(nodeIds);
            routingPolygon = List.copyOf(routingPolygon);
        }
    }

    public record Basin(String basinCode, UUID sourceNodeId, List<UUID> nodeIds) {
        public Basin {
            nodeIds = List.copyOf(nodeIds);
        }
    }

    private InfluenceBasinService() {
    }

    /** Bacia (montante) de um nó, com o polígono da área. */
    public static AreaResult basinOf(NetworkSnapshot snapshot, UUID nodeId) {
        ManeuverGraph g = new ManeuverGraph(snapshot);
        List<UUID> nodes = g.upstreamNodes(nodeId);
        return withPolygon(snapshot, nodes);
    }

    /** Área de influência (jusante) de um nó, com o polígono da área. */
    public static AreaResult influenceOf(NetworkSnapshot snapshot, UUID nodeId) {
        ManeuverGraph g = new ManeuverGraph(snapshot);
        List<UUID> nodes = g.downstreamNodes(nodeId);
        return withPolygon(snapshot, nodes);
    }

    /** Bacias por fonte: partição de todos os nós alcançáveis por alguma fonte. */
    public static List<Basin> basinsBySource(NetworkSnapshot snapshot) {
        ManeuverGraph g = new ManeuverGraph(snapshot);
        Map<UUID, NetworkNode> byId = snapshot.nodesById();
        List<NetworkNode> sources = snapshot.nodes().stream().filter(NetworkNode::source)
                .sorted(Comparator.comparing(NetworkNode::code)).toList();
        Map<UUID, List<UUID>> bySource = new HashMap<>();
        List<UUID> orphans = new ArrayList<>();
        List<NetworkNode> all = snapshot.nodes().stream()
                .sorted(Comparator.comparing(NetworkNode::code)).toList();
        for (NetworkNode n : all) {
            List<UUID> upstream = g.upstreamNodes(n.id());
            NetworkNode chosen = sources.stream().filter(s -> upstream.contains(s.id()))
                    .findFirst().orElse(null);
            if (chosen == null) orphans.add(n.id());
            else bySource.computeIfAbsent(chosen.id(), k -> new ArrayList<>()).add(n.id());
        }
        List<Basin> out = new ArrayList<>();
        for (NetworkNode s : sources) {
            List<UUID> members = bySource.getOrDefault(s.id(), List.of());
            if (!members.isEmpty()) out.add(new Basin("BACIA-" + s.code(), s.id(), members));
        }
        if (!orphans.isEmpty()) out.add(new Basin("SEM_FONTE", null, orphans));
        return List.copyOf(out);
    }

    private static AreaResult withPolygon(NetworkSnapshot snapshot, List<UUID> nodeIds) {
        Map<UUID, NetworkNode> byId = snapshot.nodesById();
        List<Point> pts = new ArrayList<>();
        for (UUID id : nodeIds) {
            NetworkNode n = byId.get(id);
            if (n.x() == null || n.y() == null) return new AreaResult(nodeIds, List.of(), false);
            pts.add(new Point(n.x(), n.y()));
        }
        return new AreaResult(nodeIds, convexHull(pts), true);
    }

    /** Envolvente convexa (Andrew monotone chain); pontos colineares internos caem fora. */
    public static List<Point> convexHull(List<Point> points) {
        List<Point> sorted = points.stream().distinct().sorted().toList();
        if (sorted.size() <= 1) return sorted;
        List<Point> lower = new ArrayList<>();
        for (Point p : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0)
                lower.remove(lower.size() - 1);
            lower.add(p);
        }
        List<Point> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Point p = sorted.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0)
                upper.remove(upper.size() - 1);
            upper.add(p);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return List.copyOf(lower);
    }

    private static double cross(Point o, Point a, Point b) {
        return (a.x() - o.x()) * (b.y() - o.y()) - (a.y() - o.y()) * (b.x() - o.x());
    }
}
