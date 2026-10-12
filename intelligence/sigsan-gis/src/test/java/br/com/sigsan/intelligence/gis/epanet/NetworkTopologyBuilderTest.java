package br.com.sigsan.intelligence.gis.epanet;

import br.com.sigsan.intelligence.gis.epanet.ElementSpec.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Cenário-base (graus, SIRGAS 2000): poço W1 em lon −46,0000; nó J1 a −46,0010; nó J2 a −46,0020,
 * todos em lat −23,0000. Cada 0,001° de longitude ≈ 102 m nessa latitude; os comprimentos dos
 * testes são os cadastrados, não os calculados.
 */
class NetworkTopologyBuilderTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private static Point pt(double lon, double lat) {
        return GF.createPoint(new Coordinate(lon, lat));
    }

    private static LineString line(double... lonLat) {
        Coordinate[] cs = new Coordinate[lonLat.length / 2];
        for (int i = 0; i < cs.length; i++) cs[i] = new Coordinate(lonLat[2 * i], lonLat[2 * i + 1]);
        return GF.createLineString(cs);
    }

    private static Map<String, BigDecimal> nums(Object... kv) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], new BigDecimal(kv[i + 1].toString()));
        return m;
    }

    private static ElementSpec well(String id, double lon, double head) {
        return new ElementSpec(id, id, Kind.RESERVOIR, pt(lon, -23.0), null, null, null, null, nums("head_m", head));
    }

    private static ElementSpec junction(String id, double lon, Double elevation, double demand) {
        Map<String, BigDecimal> n = nums("demand_lps", demand);
        if (elevation != null) n.put("elevation_m", BigDecimal.valueOf(elevation));
        return new ElementSpec(id, id, Kind.JUNCTION, pt(lon, -23.0), null, null, null, null, n);
    }

    private static ElementSpec pipe(String id, double lon1, double lon2, String length, int diameter, String material) {
        return new ElementSpec(id, id, Kind.PIPE, line(lon1, -23.0, lon2, -23.0), diameter,
                length == null ? null : new BigDecimal(length), material, null, new HashMap<>());
    }

    private static List<ElementSpec> base() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(junction("J2", -46.0020, 85.0, 1.0));
        l.add(pipe("P1", -46.0000, -46.0010, "110", 150, "PVC"));
        l.add(pipe("P2", -46.0010, -46.0020, "110", 100, "Aço"));
        return l;
    }

    @Test
    @DisplayName("tubulações ligam-se aos nós pela geometria (snap)")
    void pipesSnapToNodes() {
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", base());

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getPipes()).hasSize(2);
        EpanetPipe p1 = n.getPipes().get(0);
        assertThat(p1.getNode1()).isEqualTo("W1");
        assertThat(p1.getNode2()).isEqualTo("J1");
        assertThat(p1.getLength()).isEqualTo(110.0);
        assertThat(p1.getDiameter()).as("diâmetro em mm").isEqualTo(150.0);
        assertThat(n.getPipes().get(1).getNode1()).isEqualTo("J1");
        assertThat(n.getPipes().get(1).getNode2()).isEqualTo("J2");
    }

    @Test
    @DisplayName("contagens: bomba é ligação, não nó")
    void pumpIsALinkNotANode() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("B1", "B1", Kind.PUMP, pt(-46.0005, -23.0), null, null, null, null, nums("power_kw", 15)));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getPumps()).hasSize(1);
        // W1, J1, J2 + 2 nós virtuais da bomba
        assertThat(n.nodeCount()).isEqualTo(5);
        // P1-1, P1-2, P2 + a bomba
        assertThat(n.linkCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("válvula de isolamento divide a tubulação e conserva o comprimento total")
    void isolationValveSplitsPipeAndKeepsTotalLength() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null, null, nums()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getValves()).as("registro de isolamento é tubulação curta, não válvula de controle").isEmpty();

        EpanetPipe a = byId(n, "P1-1");
        EpanetPipe v = byId(n, "V1");
        EpanetPipe b = byId(n, "P1-2");
        assertThat(a.getNode1()).isEqualTo("W1");
        assertThat(a.getNode2()).isEqualTo("V1-i");
        assertThat(v.getNode1()).isEqualTo("V1-i");
        assertThat(v.getNode2()).isEqualTo("V1-o");
        assertThat(b.getNode1()).isEqualTo("V1-o");
        assertThat(b.getNode2()).isEqualTo("J1");

        // ponto médio: 110 · 0,499 = 54,89 de cada lado e 110 · 0,002 = 0,22 no registro
        assertThat(a.getLength()).isCloseTo(54.89, within(1e-9));
        assertThat(b.getLength()).isCloseTo(54.89, within(1e-9));
        assertThat(v.getLength()).isCloseTo(0.22, within(1e-9));
        assertThat(a.getLength() + v.getLength() + b.getLength()).isCloseTo(110.0, within(1e-9));
    }

    @Test
    @DisplayName("registro fechado fica Closed")
    void closedValveIsClosed() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null, null, nums("closed", 1)));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(byId(n, "V1").getStatus()).isFalse();
    }

    @Test
    @DisplayName("válvula de controle vira ligação VALVES e usa a cota média das pontas nos nós virtuais")
    void controlValveBecomesALinkWithInterpolatedElevation() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("PRV1", "PRV1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null,
                EpanetValve.ValveType.PRV, nums("setting", 40)));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getValves()).hasSize(1);
        EpanetValve valve = n.getValves().get(0);
        assertThat(valve.getType()).isEqualTo(EpanetValve.ValveType.PRV);
        assertThat(valve.getSetting()).isEqualByComparingTo("40");
        assertThat(valve.getDiameter()).as("herda o diâmetro da tubulação").isEqualTo(150.0);
        // W1 (carga 100) e J1 (cota 90): média 95
        EpanetJunction virtual = n.getJunctions().stream().filter(j -> j.getId().equals("PRV1-i")).findFirst().orElseThrow();
        assertThat(virtual.getElevation()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("duas válvulas na mesma tubulação são tratadas em ordem")
    void twoValvesOnTheSamePipe() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V2", "V2", Kind.VALVE, pt(-46.0007, -23.0), null, null, null, null, nums()));
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0003, -23.0), null, null, null, null, nums()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(byId(n, "P1-1").getNode2()).isEqualTo("V1-i");
        assertThat(byId(n, "P1-2").getNode1()).isEqualTo("V1-o");
        assertThat(byId(n, "P1-2").getNode2()).isEqualTo("V2-i");
        assertThat(byId(n, "P1-3").getNode1()).isEqualTo("V2-o");
        assertThat(byId(n, "P1-3").getNode2()).isEqualTo("J1");
        double total = n.getPipes().stream().filter(p -> p.getId().startsWith("P1") || p.getId().startsWith("V"))
                .mapToDouble(EpanetPipe::getLength).sum();
        assertThat(total).isCloseTo(110.0, within(1e-9));
    }

    @Test
    @DisplayName("ponta de tubulação sem nó é problema, não nó inventado")
    void danglingEndIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(pipe("P3", -46.0020, -46.0030, "110", 100, "PVC")); // nenhum nó em -46,0030
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).anyMatch(s -> s.contains("P3") && s.contains("ponta final"));
        assertThat(n.getJunctions()).extracting(EpanetJunction::getId).containsExactly("J1", "J2");
    }

    @Test
    @DisplayName("cota ausente é problema (antes virava cota 0 em silêncio)")
    void missingElevationIsAnIssue() {
        List<ElementSpec> l = base();
        l.set(1, junction("J1", -46.0010, null, 2.5));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("J1") && s.contains("sem cota"));
    }

    @Test
    @DisplayName("bomba sem potência é problema (antes assumia 50 kW)")
    void pumpWithoutPowerIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("B1", "B1", Kind.PUMP, pt(-46.0005, -23.0), null, null, null, null, nums()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("B1") && s.contains("potência"));
    }

    @Test
    @DisplayName("válvula de controle sem regulagem é problema (antes assumia PRV 50)")
    void controlValveWithoutSettingIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("PRV1", "PRV1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null,
                EpanetValve.ValveType.PRV, nums()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("PRV1") && s.contains("regulagem"));
    }

    @Test
    @DisplayName("tanque sem geometria cadastrada é problema (antes assumia 10 m de diâmetro)")
    void tankWithoutGeometryIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("T1", "T1", Kind.TANK, pt(-46.0030, -23.0), null, null, null, null, nums("elevation_m", 120)));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("T1") && s.contains("diameter_m"));
    }

    @Test
    @DisplayName("válvula fora de qualquer tubulação é problema")
    void valveOffAnyPipeIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V9", "V9", Kind.VALVE, pt(-46.0005, -23.0100), null, null, null, null, nums()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("V9") && s.contains("fora de qualquer tubulação"));
    }

    @Test
    @DisplayName("nó sem nenhuma ligação é problema")
    void isolatedNodeIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(junction("J9", -46.0100, 80.0, 0));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("J9") && s.contains("não está ligado"));
    }

    @Test
    @DisplayName("comprimento ausente é calculado da geometria, com aviso")
    void missingLengthComesFromGeometry() {
        List<ElementSpec> l = base();
        l.set(3, pipe("P1", -46.0000, -46.0010, null, 150, "PVC"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        // 0,001° de longitude a −23° de latitude ≈ 111.195 · cos(23°) · 0,001 ≈ 102,36 m
        assertThat(byId(n, "P1").getLength()).isCloseTo(102.36, within(0.5));
        assertThat(n.getWarnings()).anyMatch(s -> s.contains("P1") && s.contains("calculado da geometria"));
    }

    @Test
    @DisplayName("rugosidade de Hazen-Williams por material, com padrão conservador")
    void roughnessByMaterial() {
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", base());
        assertThat(byId(n, "P1").getRoughness()).as("PVC").isEqualTo(140.0);
        assertThat(byId(n, "P2").getRoughness()).as("Aço (sem valor específico)").isEqualTo(100.0);
        assertThat(NetworkTopologyBuilder.roughnessFor(null)).isEqualTo(100.0);
        assertThat(NetworkTopologyBuilder.roughnessFor("pead")).isEqualTo(140.0);
    }

    @Test
    @DisplayName("comprimento geodésico: 1° de latitude ≈ 111.195 m")
    void geodesicLength() {
        assertThat(NetworkTopologyBuilder.geodesicLength(line(0, 0, 0, 1))).isCloseTo(111_194.9, within(1.0));
    }

    private static EpanetPipe byId(EpanetNetwork n, String id) {
        return n.getPipes().stream().filter(p -> p.getId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("tubulação " + id + " não encontrada em "
                        + n.getPipes().stream().map(EpanetPipe::getId).toList()));
    }
}
