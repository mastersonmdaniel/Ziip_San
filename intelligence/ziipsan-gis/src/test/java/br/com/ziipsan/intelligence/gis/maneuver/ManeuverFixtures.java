package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FlowDirection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkArc;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkNode;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValvePosition;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValveState;

import java.util.List;
import java.util.UUID;

/**
 * Massa SINTÉTICA declarada da GIS-11 (nenhum dado real de rede):
 *
 * <pre>
 * S(fonte) - N1 - VA(op) - N2 - VX(INOPERAVEL) - N3 - VB(op) - N4 - N5 - Z1 (UNKNOWN)
 *                                              |
 *                                              N6
 * </pre>
 * Fluxo WITH_GEOMETRY em A1..A9; A10 (N5-Z1) UNKNOWN; A11 (N4-N5)
 * aposentado. Conexões: C1 em N2 (U1, 2 eco), C2 em N3 SENSÍVEL
 * (U2,U3, 3 eco), C3 em N6 ESSENCIAL HOSPITAL (U4, 1 eco), C4 em N5
 * (U5, 4 eco, fora da área do ensaio principal).
 * Resultado conhecido do isolamento do arco A5 (VX-N3): fechar
 * VA+VB, VX atravessada, afetados N2/VX/N3/N6 e A4/A5/A9, unidades
 * na ordem do aviso [U2,U3,U4,U1], 6 economias.
 */
public final class ManeuverFixtures {

    public static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    public static final UUID S = id(1), N1 = id(2), VA = id(3), N2 = id(4), VX = id(5),
            N3 = id(6), VB = id(7), N4 = id(8), N5 = id(9), N6 = id(10), Z1 = id(11),
            VC = id(12);
    public static final UUID A1 = id(101), A2 = id(102), A3 = id(103), A4 = id(104), A5 = id(105),
            A6 = id(106), A7 = id(107), A8 = id(108), A9 = id(109), A10 = id(110), A11 = id(111),
            A12 = id(112);
    public static final UUID U1 = id(201), U2 = id(202), U3 = id(203), U4 = id(204), U5 = id(205);
    public static final UUID U8 = id(208);
    public static final UUID TENANT = id(900), ACTOR = id(901);

    private ManeuverFixtures() {
    }

    public static NetworkSnapshot base() {
        List<NetworkNode> nodes = List.of(
                new NetworkNode(S, "S", "RESERVOIR", true, 0.0, 0.0),
                new NetworkNode(N1, "N1", "JUNCTION", false, 1.0, 0.0),
                new NetworkNode(VA, "VA", "VALVE", false, 2.0, 0.0),
                new NetworkNode(N2, "N2", "JUNCTION", false, 3.0, 0.0),
                new NetworkNode(VX, "VX", "VALVE", false, 4.0, 0.0),
                new NetworkNode(N3, "N3", "JUNCTION", false, 5.0, 0.0),
                new NetworkNode(VB, "VB", "VALVE", false, 6.0, 0.0),
                new NetworkNode(N4, "N4", "JUNCTION", false, 7.0, 0.0),
                new NetworkNode(N5, "N5", "JUNCTION", false, 8.0, 0.0),
                new NetworkNode(N6, "N6", "JUNCTION", false, 5.0, 2.0),
                new NetworkNode(Z1, "Z1", "JUNCTION", false, 9.0, 0.0));
        List<NetworkArc> arcs = List.of(
                arc(A1, "A1", S, N1, FlowDirection.WITH_GEOMETRY),
                arc(A2, "A2", N1, VA, FlowDirection.WITH_GEOMETRY),
                arc(A3, "A3", VA, N2, FlowDirection.WITH_GEOMETRY),
                arc(A4, "A4", N2, VX, FlowDirection.WITH_GEOMETRY),
                arc(A5, "A5", VX, N3, FlowDirection.WITH_GEOMETRY),
                arc(A6, "A6", N3, VB, FlowDirection.WITH_GEOMETRY),
                arc(A7, "A7", VB, N4, FlowDirection.WITH_GEOMETRY),
                arc(A8, "A8", N4, N5, FlowDirection.WITH_GEOMETRY),
                arc(A9, "A9", N3, N6, FlowDirection.WITH_GEOMETRY),
                arc(A10, "A10", N5, Z1, FlowDirection.UNKNOWN),
                new NetworkArc(A11, "A11", N4, N5, FlowDirection.WITH_GEOMETRY, "RETIRED"));
        List<ValveState> valves = List.of(
                new ValveState(VA, "VA", true, ValvePosition.OPEN),
                new ValveState(VX, "VX", false, ValvePosition.OPEN),
                new ValveState(VB, "VB", true, ValvePosition.OPEN));
        List<FunctionalConnection> conns = List.of(
                new FunctionalConnection(id(301), "C1", null, N2, id(401), id(501),
                        List.of(U1), 2, false, null),
                new FunctionalConnection(id(302), "C2", null, N3, id(402), id(502),
                        List.of(U2, U3), 3, true, null),
                new FunctionalConnection(id(303), "C3", null, N6, id(403), id(503),
                        List.of(U4), 1, false, "HOSPITAL"),
                new FunctionalConnection(id(304), "C4", null, N5, id(404), id(504),
                        List.of(U5), 4, false, null));
        return new NetworkSnapshot(nodes, arcs, valves, conns);
    }

    /**
     * Base com VA já FECHADA (sonda D1 da revisão): isolar A5 tem de
     * continuar isolável fechando só VB, com a mesma área conhecida —
     * a válvula já fechada barra a recomputação da área.
     */
    public static NetworkSnapshot withValveAClosed() {
        NetworkSnapshot b = base();
        List<ValveState> valves = List.of(
                new ValveState(VA, "VA", true, ValvePosition.CLOSED),
                new ValveState(VX, "VX", false, ValvePosition.OPEN),
                new ValveState(VB, "VB", true, ValvePosition.OPEN));
        return new NetworkSnapshot(b.nodes(), b.arcs(), valves, b.connections());
    }

    /**
     * Base + arco válvula–válvula (sonda B3-M1 da auditoria, rodada 3):
     * o arco A12 liga VB a VC (válvula operável aberta nova, nó VC),
     * com a conexão C8 derivada do próprio A12 (U8, 1 economia). Na
     * base não existe nenhum arco com válvula operável nas duas
     * pontas — isolar A12 sem a correção devolvia isolable=true com
     * área VAZIA (as duas sementes caem em barred). Resultado
     * esperado com a correção: fechar VB+VC, área = {VB, VC} e [A12],
     * afetados [U8], 1 economia.
     */
    public static NetworkSnapshot withValveToValveArc() {
        NetworkSnapshot b = base();
        List<NetworkNode> nodes = new java.util.ArrayList<>(b.nodes());
        nodes.add(new NetworkNode(VC, "VC", "VALVE", false, 6.0, 2.0));
        List<NetworkArc> arcs = new java.util.ArrayList<>(b.arcs());
        arcs.add(arc(A12, "A12", VB, VC, FlowDirection.WITH_GEOMETRY));
        List<ValveState> valves = new java.util.ArrayList<>(b.valves());
        valves.add(new ValveState(VC, "VC", true, ValvePosition.OPEN));
        List<FunctionalConnection> conns = new java.util.ArrayList<>(b.connections());
        conns.add(new FunctionalConnection(id(308), "C8", A12, null, id(408), id(508),
                List.of(U8), 1, false, null));
        return new NetworkSnapshot(nodes, arcs, valves, conns);
    }

    /**
     * Variante de withValveToValveArc() com VB e VC JÁ FECHADAS: isolar
     * A12 tem de continuar isolável, sem válvula a fechar, com a mesma
     * área {VB, VC} / [A12] — a guarda B3-M1 vale também quando as duas
     * pontas barram por já estarem fechadas (não só por estarem a fechar).
     */
    public static NetworkSnapshot withValveToValveArcBothClosed() {
        NetworkSnapshot b = withValveToValveArc();
        List<ValveState> valves = List.of(
                new ValveState(VA, "VA", true, ValvePosition.OPEN),
                new ValveState(VX, "VX", false, ValvePosition.OPEN),
                new ValveState(VB, "VB", true, ValvePosition.CLOSED),
                new ValveState(VC, "VC", true, ValvePosition.CLOSED));
        return new NetworkSnapshot(b.nodes(), b.arcs(), valves, b.connections());
    }

    /**
     * Base + C7: SEGUNDA conexão funcional do MESMO imóvel de C1
     * (propertyId id(501)), derivando de N3 com a unidade U6 — para a
     * sonda D5 (período de desabastecimento agrupado por imóvel).
     */
    public static NetworkSnapshot withPropertySharingConnections() {
        NetworkSnapshot b = base();
        List<FunctionalConnection> conns = new java.util.ArrayList<>(b.connections());
        conns.add(new FunctionalConnection(id(307), "C7", null, N3, id(407), id(501),
                List.of(id(206)), 1, false, null));
        return new NetworkSnapshot(b.nodes(), b.arcs(), b.valves(), conns);
    }

    /** Base + vínculos ruins para as regras SG-852 (C5 aposentado/vazio, C6 duplicado de C1). */
    public static NetworkSnapshot withBadConnections() {
        NetworkSnapshot b = base();
        List<FunctionalConnection> conns = new java.util.ArrayList<>(b.connections());
        conns.add(new FunctionalConnection(id(305), "C5", A11, null, id(405), id(505),
                List.of(), 0, false, null));
        conns.add(new FunctionalConnection(id(306), "C6", null, N2, id(401), id(506),
                List.of(id(209)), 1, false, null));
        return new NetworkSnapshot(b.nodes(), b.arcs(), b.valves(), conns);
    }

    private static NetworkArc arc(UUID id, String code, UUID n1, UUID n2, FlowDirection f) {
        return new NetworkArc(id, code, n1, n2, f, "IN_SERVICE");
    }
}
