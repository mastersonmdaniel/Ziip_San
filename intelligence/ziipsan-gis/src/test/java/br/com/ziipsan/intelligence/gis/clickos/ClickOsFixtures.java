package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FlowDirection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkArc;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkNode;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Massas SINTÉTICAS declaradas da II-1, sobre a massa conhecida da
 * GIS-11 ({@code ManeuverFixtures.base()} — nenhum dado real).
 */
public final class ClickOsFixtures {

    private ClickOsFixtures() {
    }

    public static UUID id(int n) {
        return ManeuverFixtures.id(n);
    }

    /**
     * Base da GIS-11 + C9: segunda conexão derivada de N6 que serve
     * a MESMA unidade U1 de C1 (derivada de N2). Na propagação a
     * jusante de N2, U1 chega por duas conexões — tem de sair UMA vez.
     */
    public static NetworkSnapshot withSharedUnit() {
        NetworkSnapshot b = ManeuverFixtures.base();
        List<FunctionalConnection> conns = new ArrayList<>(b.connections());
        conns.add(new FunctionalConnection(id(309), "C9", null, ManeuverFixtures.N6,
                id(409), id(509), List.of(ManeuverFixtures.U1), 1, false, null));
        return new NetworkSnapshot(b.nodes(), b.arcs(), b.valves(), conns);
    }

    /**
     * Rede mínima sem NENHUMA ligação: X —A→ Y, sem conexões
     * funcionais. Propagar de X expande (2 nós) mas não afecta UC
     * alguma — o caso WITHOUT_AFFECTED_UNITS.
     */
    public static NetworkSnapshot withoutConnections() {
        UUID x = id(701), y = id(702), a = id(703);
        return new NetworkSnapshot(
                List.of(new NetworkNode(x, "X", "JUNCTION", false, 0.0, 0.0),
                        new NetworkNode(y, "Y", "JUNCTION", false, 1.0, 0.0)),
                List.of(new NetworkArc(a, "AXY", x, y, FlowDirection.WITH_GEOMETRY, "IN_SERVICE")),
                List.of(), List.of());
    }
}
