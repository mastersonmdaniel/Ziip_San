package br.com.ziipsan.intelligence.gis.epanet;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Monta a rede EPANET a partir de elementos do cadastro, usando a topologia persistida
 * (GIS-03, passo M4 do plano de migração).
 *
 * <p>O que fazia a versão anterior (pré-M4): resolvia a conectividade pela geometria a cada
 * exportação — snap de cada ponta de tubulação ao nó mais próximo dentro de uma tolerância em
 * graus e válvulas/bombas localizadas sobre a tubulação mais próxima, que era dividida em dois
 * trechos. A topologia agora vem gravada no cadastro ({@code node_1}/{@code node_2} do arco,
 * expostos pela view como {@code node_1_code}/{@code node_2_code} e carregados em
 * {@link ElementSpec#node1Code()}/{@link ElementSpec#node2Code()}):
 * <ul>
 *   <li><b>Sem proximidade:</b> não existe busca de nó mais próximo nem como reserva. Código
 *       de ponta ausente ou desconhecido vira problema ({@link EpanetNetwork#getIssues()}) e a
 *       tubulação fica fora da rede; nunca se inventa nó nem se faz snap.</li>
 *   <li><b>Válvulas e bombas são nós do cadastro</b> ({@code gis_node}). No EPANET continuam
 *       sendo ligações: quando as pontas de duas tubulações referenciam o código da peça, o
 *       construtor cria UM par de nós virtuais compartilhado ({@code código-i}/{@code código-o})
 *       na posição da peça e, entre eles, a ligação curta. A tubulação que tem a peça como
 *       {@code node2} liga em {@code -i} (montante) e a que tem como {@code node1} liga em
 *       {@code -o} (jusante): o sentido do fluxo é o da direção gravada dos arcos. Duas
 *       tubulações no mesmo lado, ou peça ligada de um lado só, viram problema e a peça (e as
 *       tubulações que a referenciam) fica fora da rede.</li>
 *   <li><b>Comprimento da ligação curta:</b> sem divisão geométrica não há trecho a retirar da
 *       tubulação hospedeira, então a fração é aplicada sobre a soma dos comprimentos das duas
 *       tubulações ligadas à peça: a ligação curta mede
 *       {@code 2 · SPLIT_FRACTION · (L_montante + L_jusante)} e cada tubulação ligada é
 *       encurtada de {@code SPLIT_FRACTION · (L_montante + L_jusante)} (metade da ligação).
 *       É a mesma fração de 0,2% que o desenho antigo retirava da tubulação dividida: com a
 *       peça no meio de um trajeto de 110 m (arcos de 55 m + 55 m), os trechos saem com
 *       54,89 m e a ligação curta com 0,22 m, como antes. No registro de isolamento a ligação
 *       curta é uma tubulação, e o comprimento total da rede se conserva; em válvula de
 *       controle e bomba a ligação não tem comprimento no EPANET, e a fração retirada se
 *       perde do total de tubulações — também como antes.</li>
 *   <li><b>Sem valores inventados:</b> cota, carga do reservatório, geometria do tanque, potência
 *       da bomba e regulagem de válvula são obrigatórios. Faltando, vira problema. A versão
 *       pré-topologia gravava cota 0, bomba de 50 kW, válvula PRV de 50 e tanque de 10 m de
 *       diâmetro em silêncio, e o solver devolveria pressões plausíveis e falsas.</li>
 * </ul>
 *
 * <p>Unidades do modelo: comprimento e cota em m, diâmetro de tubulação e válvula em mm, vazão em
 * L/s, potência em kW. A geometria está em SIRGAS 2000 geográfico (graus) e só serve à posição
 * dos nós e ao comprimento geodésico das tubulações sem comprimento cadastrado — nunca à
 * conectividade.
 */
public final class NetworkTopologyBuilder {

    /** Fração retirada de cada lado da peça para a ligação curta (ver javadoc da classe). */
    static final double SPLIT_FRACTION = 0.001;

    private static final double EARTH_RADIUS_M = 6_371_008.8;
    /** C de Hazen-Williams usado quando o material não é conhecido. Valor conservador. */
    static final double DEFAULT_ROUGHNESS_C = 100.0;
    private static final double PLASTIC_ROUGHNESS_C = 140.0;

    private final EpanetNetwork network = new EpanetNetwork();
    private final Map<String, Coordinate> nodeCoordinate = new LinkedHashMap<>();
    private final Map<String, BigDecimal> nodeElevation = new HashMap<>();
    private final Map<String, Integer> nodeDegree = new HashMap<>();

    private NetworkTopologyBuilder() {
    }

    public static EpanetNetwork build(String title, String description, List<ElementSpec> elements) {
        NetworkTopologyBuilder b = new NetworkTopologyBuilder();
        b.network.setTitle(title == null ? "" : title);
        b.network.setDescription(description == null ? "" : description);
        b.run(elements);
        return b.network;
    }

    // ------------------------------------------------------------------------------------

    /** Tubulação já resolvida contra a topologia gravada, à espera das peças das pontas. */
    private static final class PipePlan {
        final ElementSpec spec;
        final LineString line;
        final double totalLength;
        final double diameter;
        final double roughness;
        /** Nó EPANET da ponta inicial: o código gravado, ou {@code peça-o} se a ponta é uma peça. */
        final String fromId;
        /** Nó EPANET da ponta final: o código gravado, ou {@code peça-i} se a ponta é uma peça. */
        final String toId;
        /** Código da peça na ponta inicial ({@code node1Code} é VALVE/PUMP), ou nulo. */
        final String fromPiece;
        /** Código da peça na ponta final ({@code node2Code} é VALVE/PUMP), ou nulo. */
        final String toPiece;
        /** Comprimento efetivo, já descontadas as frações das ligações curtas das pontas. */
        double effectiveLength;

        PipePlan(ElementSpec spec, LineString line, double totalLength, double diameter, double roughness,
                 String fromId, String toId, String fromPiece, String toPiece) {
            this.spec = spec;
            this.line = line;
            this.totalLength = totalLength;
            this.diameter = diameter;
            this.roughness = roughness;
            this.fromId = fromId;
            this.toId = toId;
            this.fromPiece = fromPiece;
            this.toPiece = toPiece;
            this.effectiveLength = totalLength;
        }
    }

    private void run(List<ElementSpec> elements) {
        Map<String, ElementSpec> byCode = new LinkedHashMap<>();
        for (ElementSpec e : elements) {
            if (byCode.putIfAbsent(e.code(), e) != null) {
                issue("Código de elemento repetido: " + e.code() + ". Só a primeira ocorrência é usada.");
            }
        }

        for (ElementSpec e : byCode.values()) {
            switch (e.kind()) {
                case JUNCTION -> addJunction(e);
                case TANK -> addTank(e);
                case RESERVOIR -> addReservoir(e);
                default -> { /* ligações e peças: abaixo */ }
            }
        }

        // Peças (válvula/bomba) são nós do cadastro: guarda a posição de cada uma. Peça sem
        // geometria de ponto já registra problema aqui e não pode virar ligação.
        Map<String, Coordinate> pieceCoordinate = new LinkedHashMap<>();
        for (ElementSpec e : byCode.values()) {
            if (e.kind() == ElementSpec.Kind.VALVE || e.kind() == ElementSpec.Kind.PUMP) {
                Coordinate c = pointOf(e);
                if (c != null) pieceCoordinate.put(e.code(), c);
            }
        }

        List<PipePlan> plans = new ArrayList<>();
        for (ElementSpec e : byCode.values()) {
            if (e.kind() == ElementSpec.Kind.PIPE) {
                PipePlan plan = planPipe(e, byCode);
                if (plan != null) plans.add(plan);
            }
        }

        // Um par de nós virtuais por peça bem ligada; devolve as peças aproveitáveis.
        Map<String, Boolean> usablePieces = linkPieces(byCode, pieceCoordinate, plans);

        for (PipePlan plan : plans) {
            addPipe(plan, usablePieces);
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

    // ---------- tubulações: resolução pelas pontas gravadas ----------

    /**
     * Resolve as pontas da tubulação pelos códigos gravados. Devolve {@code null} (com o
     * problema registrado) se alguma ponta falta, é desconhecida ou se a tubulação é um laço.
     */
    private PipePlan planPipe(ElementSpec pipe, Map<String, ElementSpec> byCode) {
        LineString line = lineOf(pipe, true);
        if (line == null) return null;

        String n1 = pipe.node1Code();
        String n2 = pipe.node2Code();
        if (n1 == null || n1.isBlank() || n2 == null || n2.isBlank()) {
            issue("Tubulação " + pipe.code() + " sem topologia gravada (node_1/node_2): "
                    + "sem os códigos dos nós das pontas ela fica fora da rede. "
                    + "Não há ligação por proximidade.");
            return null;
        }
        if (n1.equals(n2)) {
            issue("Tubulação " + pipe.code() + " começa e termina no mesmo nó (" + n1 + ").");
            return null;
        }

        String fromPiece = pieceCodeAt(n1, byCode);
        String toPiece = pieceCodeAt(n2, byCode);
        boolean fromKnown = fromPiece != null || nodeCoordinate.containsKey(n1);
        boolean toKnown = toPiece != null || nodeCoordinate.containsKey(n2);
        if (!fromKnown) {
            issue("Tubulação " + pipe.code() + ": a ponta inicial referencia o código " + n1
                    + ", que não corresponde a nenhum nó ou peça do cadastro.");
        }
        if (!toKnown) {
            issue("Tubulação " + pipe.code() + ": a ponta final referencia o código " + n2
                    + ", que não corresponde a nenhum nó ou peça do cadastro.");
        }
        if (!fromKnown || !toKnown) return null;

        double totalLength = lengthMeters(pipe, line);
        double diameter = pipe.diameterMm() == null ? 0 : pipe.diameterMm();
        if (diameter <= 0) {
            issue("Tubulação " + pipe.code() + " sem diâmetro.");
        }
        double roughness = pipe.number("roughness") != null ? pipe.number("roughness").doubleValue()
                : roughnessFor(pipe.material());

        // Ponta que é peça liga no lado jusante (-o) quando é node_1 e no montante (-i)
        // quando é node_2: o fluxo atravessa a peça no sentido gravado dos arcos.
        String fromId = fromPiece != null ? fromPiece + "-o" : n1;
        String toId = toPiece != null ? toPiece + "-i" : n2;
        return new PipePlan(pipe, line, totalLength, diameter, roughness, fromId, toId, fromPiece, toPiece);
    }

    /** Código da peça (VALVE/PUMP) com esse código, ou {@code null} se não é peça. */
    private String pieceCodeAt(String code, Map<String, ElementSpec> byCode) {
        ElementSpec e = byCode.get(code);
        return e != null && (e.kind() == ElementSpec.Kind.VALVE || e.kind() == ElementSpec.Kind.PUMP)
                ? code : null;
    }

    // ---------- peças-nó: par virtual compartilhado e ligação curta ----------

    /**
     * Para cada peça referenciada pelas tubulações, confere os dois lados e, se estiver bem
     * ligada, cria o par de nós virtuais e a ligação curta, descontando a fração combinada das
     * tubulações ligadas. Devolve o mapa código da peça → aproveitável.
     */
    private Map<String, Boolean> linkPieces(Map<String, ElementSpec> byCode,
                                            Map<String, Coordinate> pieceCoordinate,
                                            List<PipePlan> plans) {
        Map<String, Boolean> usable = new HashMap<>();
        for (ElementSpec piece : byCode.values()) {
            if (piece.kind() != ElementSpec.Kind.VALVE && piece.kind() != ElementSpec.Kind.PUMP) continue;
            String label = piece.kind() == ElementSpec.Kind.PUMP ? "Bomba " : "Válvula ";
            String code = piece.code();

            List<PipePlan> upstream = plans.stream().filter(p -> code.equals(p.toPiece)).toList();
            List<PipePlan> downstream = plans.stream().filter(p -> code.equals(p.fromPiece)).toList();

            if (!pieceCoordinate.containsKey(code)) {
                // pointOf já registrou o problema da geometria.
                usable.put(code, false);
                continue;
            }
            if (upstream.isEmpty() && downstream.isEmpty()) {
                issue(label + code + " não está ligada a nenhuma tubulação: no cadastro ela é um nó "
                        + "e só entra no modelo entre duas tubulações que a referenciem.");
                usable.put(code, false);
                continue;
            }
            if (upstream.size() > 1) {
                issue(label + code + " tem " + upstream.size() + " tubulações ligadas no mesmo lado "
                        + "(montante: " + codesOf(upstream) + "). Uma peça-nó aceita uma tubulação "
                        + "de cada lado; as demais ligações precisam de nó próprio no cadastro.");
                usable.put(code, false);
                continue;
            }
            if (downstream.size() > 1) {
                issue(label + code + " tem " + downstream.size() + " tubulações ligadas no mesmo lado "
                        + "(jusante: " + codesOf(downstream) + "). Uma peça-nó aceita uma tubulação "
                        + "de cada lado; as demais ligações precisam de nó próprio no cadastro.");
                usable.put(code, false);
                continue;
            }
            if (upstream.isEmpty() || downstream.isEmpty()) {
                issue(label + code + " está ligada a tubulação de um lado só ("
                        + (upstream.isEmpty() ? "sem tubulação a montante" : "sem tubulação a jusante")
                        + "): a ligação curta precisa dos dois lados e a peça fica fora da rede.");
                usable.put(code, false);
                continue;
            }

            PipePlan up = upstream.get(0);
            PipePlan down = downstream.get(0);
            double linkedTotal = up.totalLength + down.totalLength;
            double shortLength = 2 * SPLIT_FRACTION * linkedTotal;
            up.effectiveLength -= SPLIT_FRACTION * linkedTotal;
            down.effectiveLength -= SPLIT_FRACTION * linkedTotal;

            String nodeIn = code + "-i";
            String nodeOut = code + "-o";
            BigDecimal elevation = pieceElevation(piece, up, down, byCode, plans);
            Coordinate c = pieceCoordinate.get(code);
            addVirtualJunction(nodeIn, c, elevation);
            addVirtualJunction(nodeOut, c, elevation);

            addPointLink(piece, nodeIn, nodeOut, shortLength, up.diameter, up.roughness);
            usable.put(code, true);
        }
        return usable;
    }

    private String codesOf(List<PipePlan> plans) {
        return String.join(", ", plans.stream().map(p -> p.spec.code()).toList());
    }

    /**
     * Cota dos nós virtuais da peça: a do próprio cadastro da peça ({@code elevation_m}); sem
     * ela, a média das cotas dos nós das outras pontas das tubulações ligadas — o mesmo critério
     * da versão anterior, que interpolava entre as pontas da tubulação dividida.
     */
    private BigDecimal pieceElevation(ElementSpec piece, PipePlan up, PipePlan down,
                                      Map<String, ElementSpec> byCode, List<PipePlan> plans) {
        BigDecimal elevation = piece.number("elevation_m");
        if (elevation != null) return elevation;
        Set<String> visited = new HashSet<>();
        visited.add(piece.code());
        BigDecimal from = farElevation(up.spec.node1Code(), byCode, plans, visited);
        BigDecimal to = farElevation(down.spec.node2Code(), byCode, plans, visited);
        if (from != null && to != null) {
            return from.add(to).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        }
        issue("Não foi possível determinar a cota dos nós de " + piece.code()
                + ": informe elevation_m no elemento ou cotas nos nós das pontas das tubulações ligadas.");
        return null;
    }

    /**
     * Cota conhecida de um código de ponta: nó real pelo mapa; peça, pelo seu
     * {@code elevation_m} — e, sem ele, pela propagação através da cadeia de
     * peças (correção M3, D3): duas peças-nó em sequência (válvula–válvula)
     * não têm nó real entre elas, então a cota da ponta distante atravessa a
     * peça vizinha até o próximo nó real de cada lado. A peça vizinha
     * empresta a média dos dois lados dela quando ambos resolvem; quando só
     * um lado resolve (o outro é a própria cadeia de onde a busca veio,
     * cortada pelo conjunto {@code visited}), empresta esse lado — é ele que
     * permite à peça de origem tirar a média das suas duas pontas reais. A
     * regra da peça de origem não muda: ela continua exigindo as duas pontas
     * resolvidas (ver {@link #pieceElevation}); a propagação só deixa de
     * parar na primeira peça sem {@code elevation_m} cadastrado.
     */
    private BigDecimal farElevation(String code, Map<String, ElementSpec> byCode,
                                    List<PipePlan> plans, Set<String> visited) {
        if (code == null) return null;
        BigDecimal e = nodeElevation.get(code);
        if (e != null) return e;
        ElementSpec other = byCode.get(code);
        if (other == null) return null;
        BigDecimal own = other.number("elevation_m");
        if (own != null) return own;
        if (other.kind() != ElementSpec.Kind.VALVE && other.kind() != ElementSpec.Kind.PUMP) {
            return null;
        }
        if (!visited.add(code)) return null;
        List<PipePlan> neighborUp = plans.stream().filter(p -> code.equals(p.toPiece)).toList();
        List<PipePlan> neighborDown = plans.stream().filter(p -> code.equals(p.fromPiece)).toList();
        if (neighborUp.size() != 1 || neighborDown.size() != 1) {
            // Cadeia ambígua ou peça ligada de um lado só: sem propagação
            // conclusiva (a ligação da peça já é problema em linkPieces).
            return null;
        }
        BigDecimal from = farElevation(neighborUp.get(0).spec.node1Code(), byCode, plans, visited);
        BigDecimal to = farElevation(neighborDown.get(0).spec.node2Code(), byCode, plans, visited);
        if (from != null && to != null) {
            return from.add(to).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        }
        return from != null ? from : to;
    }

    private void addPointLink(ElementSpec spec, String nodeIn, String nodeOut, double shortLength,
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
            // comprimento retirado das tubulações ligadas volta aqui, então o total é conservado.
            boolean closed = spec.number("closed") != null && spec.number("closed").signum() != 0;
            network.getPipes().add(newPipe(spec.code(), spec.name(), nodeIn, nodeOut,
                    shortLength, diameter, roughness, !closed));
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

    // ---------- tubulações: emissão ----------

    private void addPipe(PipePlan plan, Map<String, Boolean> usablePieces) {
        for (String piece : new String[]{plan.fromPiece, plan.toPiece}) {
            if (piece != null && !Boolean.TRUE.equals(usablePieces.get(piece))) {
                issue("Tubulação " + plan.spec.code() + " referencia a peça " + piece
                        + ", que não pôde ser ligada (ver o problema da peça); a tubulação fica fora da rede.");
                return;
            }
        }
        if (!(plan.effectiveLength > 0)) {
            issue("Tubulação " + plan.spec.code() + " ficou sem comprimento positivo depois de "
                    + "descontadas as ligações curtas das peças das pontas.");
            return;
        }
        EpanetPipe p = newPipe(plan.spec.code(), plan.spec.name(), plan.fromId, plan.toId,
                plan.effectiveLength, plan.diameter, plan.roughness, true);
        List<Coordinate> vertices = new ArrayList<>();
        for (int i = 1; i < plan.line.getNumPoints() - 1; i++) vertices.add(plan.line.getCoordinateN(i));
        if (!vertices.isEmpty()) network.getLinkVertices().put(plan.spec.code(), vertices);
        network.getPipes().add(p);
        touch(plan.fromId);
        touch(plan.toId);
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
