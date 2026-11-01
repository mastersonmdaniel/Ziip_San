package br.com.ziipsan.intelligence.gis.epanet;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monta a rede EPANET a partir de elementos do cadastro: resolve a topologia pela geometria.
 *
 * <p>O que fazia a versão anterior: criava tubulações, bombas e válvulas com os nós {@code ""},
 * então o arquivo .inp nunca tinha ligação entre elementos. Aqui:
 * <ul>
 *   <li><b>Snap:</b> cada ponta de tubulação é ligada ao nó mais próximo dentro da tolerância.
 *       Ponta sem nó vira problema ({@link EpanetNetwork#getIssues()}), nunca um nó inventado.</li>
 *   <li><b>Elementos pontuais (válvula, bomba):</b> no EPANET são ligações, não nós. O ponto é
 *       localizado sobre uma tubulação, que é dividida em dois trechos, e entre eles entram dois
 *       nós virtuais e a ligação. Várias válvulas na mesma tubulação são tratadas em sequência.</li>
 *   <li><b>Sem valores inventados:</b> cota, carga do reservatório, geometria do tanque, potência
 *       da bomba e regulagem de válvula são obrigatórios. Faltando, vira problema. A versão anterior
 *       gravava cota 0, bomba de 50 kW, válvula PRV de 50 e tanque de 10 m de diâmetro em silêncio,
 *       e o solver devolveria pressões plausíveis e falsas.</li>
 * </ul>
 *
 * <p>Unidades do modelo: comprimento e cota em m, diâmetro de tubulação e válvula em mm, vazão em
 * L/s, potência em kW. A geometria está em SIRGAS 2000 geográfico (graus): a tolerância de snap é
 * em graus (0,00001° ≈ 1,1 m).
 *
 * <p>Limitações conhecidas: a direção da bomba e da válvula segue o sentido de digitalização da
 * tubulação; o comprimento retirado para a ligação (0,2% da tubulação) é conservado quando a
 * válvula é de isolamento (vira um trecho curto) e perdido nas válvulas de controle.
 */
public final class NetworkTopologyBuilder {

    /** ≈ 1,1 m em graus de latitude. */
    public static final double DEFAULT_SNAP_TOLERANCE_DEGREES = 0.00001;

    /** Fração da tubulação, de cada lado do ponto, ocupada pelos nós virtuais da ligação. */
    static final double SPLIT_FRACTION = 0.001;

    private static final double EARTH_RADIUS_M = 6_371_008.8;
    /** C de Hazen-Williams usado quando o material não é conhecido. Valor conservador. */
    static final double DEFAULT_ROUGHNESS_C = 100.0;
    private static final double PLASTIC_ROUGHNESS_C = 140.0;

    private final double tolerance;
    private final EpanetNetwork network = new EpanetNetwork();
    private final Map<String, Coordinate> nodeCoordinate = new LinkedHashMap<>();
    private final Map<String, BigDecimal> nodeElevation = new HashMap<>();
    private final Map<String, Integer> nodeDegree = new HashMap<>();

    private NetworkTopologyBuilder(double tolerance) {
        this.tolerance = tolerance;
    }

    public static EpanetNetwork build(String title, String description, List<ElementSpec> elements) {
        return build(title, description, elements, DEFAULT_SNAP_TOLERANCE_DEGREES);
    }

    public static EpanetNetwork build(String title, String description, List<ElementSpec> elements,
                                      double snapToleranceDegrees) {
        NetworkTopologyBuilder b = new NetworkTopologyBuilder(snapToleranceDegrees);
        b.network.setTitle(title == null ? "" : title);
        b.network.setDescription(description == null ? "" : description);
        b.run(elements);
        return b.network;
    }

    // ------------------------------------------------------------------------------------

    private void run(List<ElementSpec> elements) {
        for (ElementSpec e : elements) {
            switch (e.kind()) {
                case JUNCTION -> addJunction(e);
                case TANK -> addTank(e);
                case RESERVOIR -> addReservoir(e);
                default -> { /* ligacoes: abaixo */ }
            }
        }

        List<ElementSpec> pipes = elements.stream().filter(e -> e.kind() == ElementSpec.Kind.PIPE).toList();
        Map<String, List<PointLink>> linksByPipe = new HashMap<>();
        for (ElementSpec e : elements) {
            if (e.kind() == ElementSpec.Kind.VALVE || e.kind() == ElementSpec.Kind.PUMP) {
                attachToPipe(e, pipes, linksByPipe);
            }
        }

        for (ElementSpec pipe : pipes) {
            addPipeWithLinks(pipe, linksByPipe.getOrDefault(pipe.code(), List.of()));
        }

        nodeCoordinate.forEach((id, c) -> network.getNodeCoordinates().put(id, c));
        for (String id : nodeCoordinate.keySet()) {
            if (nodeDegree.getOrDefault(id, 0) == 0) {
                issue("Nó " + id + " não está ligado a nenhuma tubulação, bomba ou válvula.");
            }
        }
    }

    // ---------- nós ----------

    private void addJunction(ElementSpec e) {
        Coordinate c = pointOf(e);
        if (c == null) return;
        BigDecimal elevation = e.number("elevation_m");
        if (elevation == null) {
            issue("Nó " + e.code() + " sem cota (elevation_m). O EPANET precisa da cota de cada nó; "
                    + "cota 0 gera pressões falsas.");
        }
        EpanetJunction j = new EpanetJunction();
        j.setId(e.code());
        j.setName(e.name());
        j.setElevation(elevation);
        BigDecimal demand = e.number("demand_lps");
        j.setDemand(demand == null ? BigDecimal.ZERO : demand);
        j.setCoordinates(c);
        network.getJunctions().add(j);
        registerNode(e.code(), c, elevation);
    }

    private void addTank(ElementSpec e) {
        Coordinate c = pointOf(e);
        if (c == null) return;
        List<String> missing = new ArrayList<>();
        for (String key : List.of("elevation_m", "init_level_m", "min_level_m", "max_level_m", "diameter_m")) {
            if (e.number(key) == null) missing.add(key);
        }
        if (!missing.isEmpty()) {
            issue("Tanque " + e.code() + " sem " + String.join(", ", missing)
                    + ". A geometria do tanque não é estimada.");
        }
        EpanetTank t = new EpanetTank();
        t.setId(e.code());
        t.setName(e.name());
        t.setElevation(e.number("elevation_m"));
        t.setInitLevel(e.number("init_level_m"));
        t.setMinLevel(e.number("min_level_m"));
        t.setMaxLevel(e.number("max_level_m"));
        t.setDiameter(e.number("diameter_m"));
        t.setMinVolume(e.number("min_volume_m3") == null ? BigDecimal.ZERO : e.number("min_volume_m3"));
        network.getTanks().add(t);
        registerNode(e.code(), c, e.number("elevation_m"));
    }

    private void addReservoir(ElementSpec e) {
        Coordinate c = pointOf(e);
        if (c == null) return;
        BigDecimal head = e.number("head_m");
        if (head == null) {
            issue("Reservatório de nível fixo " + e.code() + " sem carga (head_m).");
        }
        EpanetReservoir r = new EpanetReservoir();
        r.setId(e.code());
        r.setName(e.name());
        r.setHead(head);
        network.getReservoirs().add(r);
        registerNode(e.code(), c, head);
    }

    private void registerNode(String id, Coordinate c, BigDecimal elevation) {
        if (nodeCoordinate.putIfAbsent(id, c) != null) {
            issue("ID de nó repetido: " + id);
        }
        if (elevation != null) nodeElevation.put(id, elevation);
    }

    private Coordinate pointOf(ElementSpec e) {
        if (e.geometry() instanceof Point p && !p.isEmpty()) {
            return p.getCoordinate();
        }
        issue("Elemento " + e.code() + " deveria ter geometria de ponto.");
        return null;
    }

    // ---------- ligações pontuais ----------

    /** Ligação pontual (válvula/bomba) posicionada sobre uma tubulação. */
    private record PointLink(ElementSpec spec, double fraction) {
    }

    private void attachToPipe(ElementSpec link, List<ElementSpec> pipes, Map<String, List<PointLink>> out) {
        Coordinate c = pointOf(link);
        if (c == null) return;
        Point p = link.geometry().getFactory().createPoint(c);

        ElementSpec best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ElementSpec pipe : pipes) {
            LineString line = lineOf(pipe, false);
            if (line == null) continue;
            double d = line.distance(p);
            if (d < bestDistance) {
                bestDistance = d;
                best = pipe;
            }
        }
        if (best == null || bestDistance > tolerance) {
            issue((link.kind() == ElementSpec.Kind.PUMP ? "Bomba " : "Válvula ") + link.code()
                    + " está fora de qualquer tubulação (tolerância " + tolerance + " graus).");
            return;
        }
        LineString line = lineOf(best, false);
        LengthIndexedLine indexed = new LengthIndexedLine(line);
        double fraction = indexed.project(c) / line.getLength();
        out.computeIfAbsent(best.code(), k -> new ArrayList<>()).add(new PointLink(link, fraction));
    }

    // ---------- tubulações ----------

    private void addPipeWithLinks(ElementSpec pipe, List<PointLink> rawLinks) {
        LineString line = lineOf(pipe, true);
        if (line == null) return;

        Coordinate start = line.getCoordinateN(0);
        Coordinate end = line.getCoordinateN(line.getNumPoints() - 1);
        String from = nearestNode(start);
        String to = nearestNode(end);
        if (from == null) issue("Tubulação " + pipe.code() + ": a ponta inicial não encontra nó dentro da tolerância.");
        if (to == null) issue("Tubulação " + pipe.code() + ": a ponta final não encontra nó dentro da tolerância.");
        if (from == null || to == null) return;
        if (from.equals(to)) {
            issue("Tubulação " + pipe.code() + " começa e termina no mesmo nó (" + from + ").");
            return;
        }

        double totalLength = lengthMeters(pipe, line);
        double diameter = pipe.diameterMm() == null ? 0 : pipe.diameterMm();
        if (diameter <= 0) {
            issue("Tubulação " + pipe.code() + " sem diâmetro.");
        }
        double roughness = pipe.number("roughness") != null ? pipe.number("roughness").doubleValue()
                : roughnessFor(pipe.material());

        List<PointLink> links = new ArrayList<>(rawLinks);
        links.sort((a, b) -> Double.compare(a.fraction(), b.fraction()));

        if (links.isEmpty()) {
            EpanetPipe p = newPipe(pipe.code(), pipe.name(), from, to, totalLength, diameter, roughness, true);
            List<Coordinate> vertices = new ArrayList<>();
            for (int i = 1; i < line.getNumPoints() - 1; i++) vertices.add(line.getCoordinateN(i));
            if (!vertices.isEmpty()) network.getLinkVertices().put(pipe.code(), vertices);
            network.getPipes().add(p);
            touch(from);
            touch(to);
            return;
        }

        LengthIndexedLine indexed = new LengthIndexedLine(line);
        double lineLength = line.getLength();
        BigDecimal elevationFrom = nodeElevation.get(from);
        BigDecimal elevationTo = nodeElevation.get(to);

        String previousNode = from;
        double previousFraction = 0.0;
        int segment = 1;

        for (PointLink pl : links) {
            double tIn = pl.fraction() - SPLIT_FRACTION;
            double tOut = pl.fraction() + SPLIT_FRACTION;
            if (tIn <= previousFraction || tOut >= 1.0) {
                issue((pl.spec().kind() == ElementSpec.Kind.PUMP ? "Bomba " : "Válvula ") + pl.spec().code()
                        + " está colada a outro elemento ou a uma ponta da tubulação " + pipe.code()
                        + "; não há espaço para dividi-la.");
                continue;
            }

            String id = pl.spec().code();
            String nodeIn = id + "-i";
            String nodeOut = id + "-o";
            BigDecimal elevation = pl.spec().number("elevation_m");
            if (elevation == null && elevationFrom != null && elevationTo != null) {
                elevation = elevationFrom.add(elevationTo).divide(BigDecimal.valueOf(2), 3, java.math.RoundingMode.HALF_UP);
            }
            if (elevation == null) {
                issue("Não foi possível determinar a cota dos nós de " + id
                        + ": informe elevation_m no elemento ou cotas nas pontas da tubulação " + pipe.code() + ".");
            }
            Coordinate cIn = indexed.extractPoint(tIn * lineLength);
            Coordinate cOut = indexed.extractPoint(tOut * lineLength);
            addVirtualJunction(nodeIn, cIn, elevation);
            addVirtualJunction(nodeOut, cOut, elevation);

            // trecho anterior -> nó de entrada
            network.getPipes().add(newPipe(pipe.code() + "-" + segment++, pipe.name(), previousNode, nodeIn,
                    totalLength * (tIn - previousFraction), diameter, roughness, true));
            touch(previousNode);
            touch(nodeIn);

            addPointLink(pl.spec(), nodeIn, nodeOut, totalLength * (tOut - tIn), diameter, roughness);

            previousNode = nodeOut;
            previousFraction = tOut;
        }

        network.getPipes().add(newPipe(pipe.code() + "-" + segment, pipe.name(), previousNode, to,
                totalLength * (1.0 - previousFraction), diameter, roughness, true));
        touch(previousNode);
        touch(to);
    }

    private void addPointLink(ElementSpec spec, String nodeIn, String nodeOut, double carvedLength,
                              double pipeDiameter, double roughness) {
        touch(nodeIn);
        touch(nodeOut);
        if (spec.kind() == ElementSpec.Kind.PUMP) {
            BigDecimal power = spec.number("power_kw");
            if (power == null) {
                issue("Bomba " + spec.code() + " sem potência (power_kw) nem curva. A potência não é estimada.");
            }
            EpanetPump pump = new EpanetPump();
            pump.setId(spec.code());
            pump.setName(spec.name());
            pump.setNode1(nodeIn);
            pump.setNode2(nodeOut);
            pump.setPower(power);
            network.getPumps().add(pump);
            return;
        }

        double diameter = spec.diameterMm() != null ? spec.diameterMm() : pipeDiameter;
        if (spec.valveType() == null) {
            // Registro de isolamento: no EPANET é uma tubulação curta que pode ficar fechada. O
            // comprimento retirado da tubulação volta aqui, então o total é conservado.
            boolean closed = spec.number("closed") != null && spec.number("closed").signum() != 0;
            network.getPipes().add(newPipe(spec.code(), spec.name(), nodeIn, nodeOut,
                    carvedLength, diameter, roughness, !closed));
            return;
        }

        BigDecimal setting = spec.number("setting");
        if (setting == null) {
            issue("Válvula " + spec.code() + " (" + spec.valveType() + ") sem regulagem (setting). "
                    + "A regulagem não é estimada.");
        }
        EpanetValve valve = new EpanetValve();
        valve.setId(spec.code());
        valve.setName(spec.name());
        valve.setNode1(nodeIn);
        valve.setNode2(nodeOut);
        valve.setDiameter(diameter);
        valve.setType(spec.valveType());
        valve.setSetting(setting);
        network.getValves().add(valve);
    }

    private void addVirtualJunction(String id, Coordinate c, BigDecimal elevation) {
        EpanetJunction j = new EpanetJunction();
        j.setId(id);
        j.setName("nó virtual");
        j.setElevation(elevation);
        j.setDemand(BigDecimal.ZERO);
        j.setCoordinates(c);
        network.getJunctions().add(j);
        registerNode(id, c, elevation);
    }

    private EpanetPipe newPipe(String id, String name, String n1, String n2, double length, double diameter,
                               double roughness, boolean open) {
        EpanetPipe p = new EpanetPipe();
        p.setId(id);
        p.setName(name);
        p.setNode1(n1);
        p.setNode2(n2);
        p.setLength(length);
        p.setDiameter(diameter);
        p.setRoughness(roughness);
        p.setStatus(open ? 1 : 0);
        return p;
    }

    private void touch(String node) {
        nodeDegree.merge(node, 1, Integer::sum);
    }

    // ---------- geometria ----------

    private String nearestNode(Coordinate c) {
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Coordinate> e : nodeCoordinate.entrySet()) {
            double d = e.getValue().distance(c);
            if (d < bestDistance) {
                bestDistance = d;
                best = e.getKey();
            }
        }
        return best != null && bestDistance <= tolerance ? best : null;
    }

    private LineString lineOf(ElementSpec e, boolean reportProblem) {
        Geometry g = e.geometry();
        if (g instanceof LineString ls && ls.getNumPoints() >= 2) return ls;
        if (g instanceof MultiLineString m && m.getNumGeometries() == 1
                && m.getGeometryN(0) instanceof LineString ls && ls.getNumPoints() >= 2) {
            return ls;
        }
        if (reportProblem) issue("Tubulação " + e.code() + " deveria ter geometria de linha com ao menos 2 pontos.");
        return null;
    }

    private double lengthMeters(ElementSpec pipe, LineString line) {
        if (pipe.lengthM() != null && pipe.lengthM().signum() > 0) {
            return pipe.lengthM().doubleValue();
        }
        double meters = geodesicLength(line);
        network.getWarnings().add("Tubulação " + pipe.code() + " sem comprimento cadastrado: calculado da "
                + "geometria (" + Math.round(meters * 100) / 100.0 + " m).");
        return meters;
    }

    /** Comprimento da linha em metros pela fórmula de haversine (x = longitude, y = latitude). */
    static double geodesicLength(LineString line) {
        double sum = 0;
        for (int i = 1; i < line.getNumPoints(); i++) {
            Coordinate a = line.getCoordinateN(i - 1);
            Coordinate b = line.getCoordinateN(i);
            double lat1 = Math.toRadians(a.y), lat2 = Math.toRadians(b.y);
            double dLat = lat2 - lat1;
            double dLon = Math.toRadians(b.x - a.x);
            double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            sum += 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
        }
        return sum;
    }

    /**
     * C de Hazen-Williams por material. Valores típicos de livro-texto, pensados como padrão que
     * cada tenant deve poder sobrescrever (atributo {@code roughness} do elemento).
     */
    static double roughnessFor(String material) {
        if (material == null) return DEFAULT_ROUGHNESS_C;
        String m = Normalizer.normalize(material, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).trim();
        if (m.equals("PVC") || m.equals("PEAD") || m.equals("PEBD") || m.equals("PE") || m.equals("PRFV")) {
            return PLASTIC_ROUGHNESS_C;
        }
        return DEFAULT_ROUGHNESS_C;
    }

    private void issue(String message) {
        network.getIssues().add(message);
    }
}
