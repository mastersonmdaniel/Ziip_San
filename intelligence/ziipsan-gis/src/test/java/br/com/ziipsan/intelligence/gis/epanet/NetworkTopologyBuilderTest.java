package br.com.ziipsan.intelligence.gis.epanet;

import br.com.ziipsan.intelligence.gis.epanet.ElementSpec.Kind;
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
 *
 * <p>Desde a GIS-03 (M4) a topologia é a gravada: cada tubulação declara {@code node1Code} e
 * {@code node2Code} e a geometria NÃO decide ligação nenhuma. Válvulas e bombas são nós do
 * cadastro: entram entre duas tubulações que as referenciam e viram a ligação curta entre os
 * nós virtuais {@code código-i}/{@code código-o}. Onde a topologia antiga (snap) era correta,
 * as redes e os comprimentos esperados são os mesmos de antes do M4.
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

    /** Tubulação com as pontas gravadas: a linha vai de lon1 a lon2, o arco vai de n1 a n2. */
    private static ElementSpec pipe(String id, double lon1, double lon2, String length, int diameter, String material,
                                    String n1, String n2) {
        return new ElementSpec(id, id, Kind.PIPE, line(lon1, -23.0, lon2, -23.0), diameter,
                length == null ? null : new BigDecimal(length), material, null, new HashMap<>(), n1, n2);
    }

    private static List<ElementSpec> base() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(junction("J2", -46.0020, 85.0, 1.0));
        l.add(pipe("P1", -46.0000, -46.0010, "110", 150, "PVC", "W1", "J1"));
        l.add(pipe("P2", -46.0010, -46.0020, "110", 100, "Aço", "J1", "J2"));
        return l;
    }

    @Test
    @DisplayName("tubulações ligam-se pelos códigos gravados das pontas (node_1/node_2)")
    void pipesUsePersistedTopology() {
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
    @DisplayName("a geometria não decide ligação: linha longe dos nós, códigos mandam (sem snap)")
    void geometryDoesNotDecideTopology() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        // A linha está deslocada ~55 m dos dois nós: pelo snap antigo, nenhuma ponta ligaria.
        l.add(new ElementSpec("P1", "P1", Kind.PIPE, line(-46.0005, -23.0005, -46.0006, -23.0005), 150,
                new BigDecimal("110"), "PVC", null, new HashMap<>(), "W1", "J1"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getPipes()).hasSize(1);
        assertThat(n.getPipes().get(0).getNode1()).isEqualTo("W1");
        assertThat(n.getPipes().get(0).getNode2()).isEqualTo("J1");
    }

    @Test
    @DisplayName("contagens: bomba-nó é ligação entre dois nós virtuais, não nó")
    void pumpNodeIsALinkNotANode() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(junction("J2", -46.0020, 85.0, 1.0));
        l.add(new ElementSpec("B1", "B1", Kind.PUMP, pt(-46.0005, -23.0), null, null, null, null, nums("power_kw", 15)));
        l.add(pipe("P1", -46.0000, -46.0005, "110", 150, "PVC", "W1", "B1"));
        l.add(pipe("P2", -46.0005, -46.0010, "110", 150, "PVC", "B1", "J1"));
        l.add(pipe("P3", -46.0010, -46.0020, "110", 100, "Aço", "J1", "J2"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getPumps()).hasSize(1);
        assertThat(n.getPumps().get(0).getNode1()).isEqualTo("B1-i");
        assertThat(n.getPumps().get(0).getNode2()).isEqualTo("B1-o");
        // W1, J1, J2 + 2 nós virtuais da bomba
        assertThat(n.nodeCount()).isEqualTo(5);
        // P1, P2, P3 + a bomba
        assertThat(n.linkCount()).isEqualTo(4);
        // A fração da ligação curta sai das tubulações: 110 − 0,22 de cada lado da bomba,
        // como quando a bomba dividia uma tubulação de 220 m pelo meio.
        assertThat(n.totalPipeLength()).isCloseTo(110 - 0.22 + 110 - 0.22 + 110, within(1e-9));
    }

    @Test
    @DisplayName("válvula de isolamento entre duas tubulações gera a ligação curta e conserva o comprimento total")
    void isolationValveNodeKeepsTotalLength() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(junction("J2", -46.0020, 85.0, 1.0));
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null, null, nums()));
        l.add(pipe("P1", -46.0000, -46.0005, "55", 150, "PVC", "W1", "V1"));
        l.add(pipe("P2", -46.0005, -46.0010, "55", 150, "PVC", "V1", "J1"));
        l.add(pipe("P3", -46.0010, -46.0020, "110", 100, "Aço", "J1", "J2"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getValves()).as("registro de isolamento é tubulação curta, não válvula de controle").isEmpty();

        EpanetPipe a = byId(n, "P1");
        EpanetPipe v = byId(n, "V1");
        EpanetPipe b = byId(n, "P2");
        assertThat(a.getNode1()).isEqualTo("W1");
        assertThat(a.getNode2()).isEqualTo("V1-i");
        assertThat(v.getNode1()).isEqualTo("V1-i");
        assertThat(v.getNode2()).isEqualTo("V1-o");
        assertThat(b.getNode1()).isEqualTo("V1-o");
        assertThat(b.getNode2()).isEqualTo("J1");

        // Trajeto de 110 m com a peça no meio, como no teste pré-M4: 54,89 de cada lado e
        // 0,22 no registro (0,2% de 110 m). Os trechos agora são as tubulações cadastradas
        // P1 e P2, sem os sufixos -1/-2 da divisão geométrica antiga.
        assertThat(a.getLength()).isCloseTo(54.89, within(1e-9));
        assertThat(b.getLength()).isCloseTo(54.89, within(1e-9));
        assertThat(v.getLength()).isCloseTo(0.22, within(1e-9));
        assertThat(a.getLength() + v.getLength() + b.getLength()).isCloseTo(110.0, within(1e-9));
        assertThat(n.totalPipeLength()).isCloseTo(220.0, within(1e-9));
    }

    @Test
    @DisplayName("registro fechado fica Closed")
    void closedValveIsClosed() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null, null, nums("closed", 1)));
        l.add(pipe("P1", -46.0000, -46.0005, "55", 150, "PVC", "W1", "V1"));
        l.add(pipe("P2", -46.0005, -46.0010, "55", 150, "PVC", "V1", "J1"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(byId(n, "V1").getStatus()).isFalse();
    }

    @Test
    @DisplayName("válvula de controle vira ligação VALVES e usa a cota média das pontas nos nós virtuais")
    void controlValveBecomesALinkWithInterpolatedElevation() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(junction("J2", -46.0020, 85.0, 1.0));
        l.add(new ElementSpec("PRV1", "PRV1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null,
                EpanetValve.ValveType.PRV, nums("setting", 40)));
        l.add(pipe("P1", -46.0000, -46.0005, "55", 150, "PVC", "W1", "PRV1"));
        l.add(pipe("P2", -46.0005, -46.0010, "55", 150, "PVC", "PRV1", "J1"));
        l.add(pipe("P3", -46.0010, -46.0020, "110", 100, "Aço", "J1", "J2"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(n.getValves()).hasSize(1);
        EpanetValve valve = n.getValves().get(0);
        assertThat(valve.getNode1()).isEqualTo("PRV1-i");
        assertThat(valve.getNode2()).isEqualTo("PRV1-o");
        assertThat(valve.getType()).isEqualTo(EpanetValve.ValveType.PRV);
        assertThat(valve.getSetting()).isEqualByComparingTo("40");
        assertThat(valve.getDiameter()).as("herda o diâmetro da tubulação de montante").isEqualTo(150.0);
        // W1 (carga 100) e J1 (cota 90): média 95
        EpanetJunction virtual = n.getJunctions().stream().filter(j -> j.getId().equals("PRV1-i")).findFirst().orElseThrow();
        assertThat(virtual.getElevation()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("duas peças-nó em sequência ligam-se pelos lados certos do par virtual")
    void twoPieceNodesInSequence() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0003, -23.0), null, null, null, null, nums()));
        l.add(new ElementSpec("V2", "V2", Kind.VALVE, pt(-46.0007, -23.0), null, null, null, null, nums()));
        l.add(pipe("P1", -46.0000, -46.0003, "110", 150, "PVC", "W1", "V1"));
        l.add(pipe("P2", -46.0003, -46.0007, "110", 150, "PVC", "V1", "V2"));
        l.add(pipe("P3", -46.0007, -46.0010, "110", 150, "PVC", "V2", "J1"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).isEmpty();
        assertThat(byId(n, "P1").getNode2()).isEqualTo("V1-i");
        assertThat(byId(n, "P2").getNode1()).isEqualTo("V1-o");
        assertThat(byId(n, "P2").getNode2()).isEqualTo("V2-i");
        assertThat(byId(n, "P3").getNode1()).isEqualTo("V2-o");
        assertThat(byId(n, "P3").getNode2()).isEqualTo("J1");
        // Cada ligação curta retira 0,22 de cada tubulação ligada; o total se conserva.
        double total = n.getPipes().stream()
                .mapToDouble(EpanetPipe::getLength).sum();
        assertThat(total).isCloseTo(330.0, within(1e-9));
    }

    @Test
    @DisplayName("ponta de tubulação com código desconhecido é problema, não nó inventado")
    void danglingEndIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(pipe("P3", -46.0020, -46.0030, "110", 100, "PVC", "J2", "JX")); // JX não existe
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).anyMatch(s -> s.contains("P3") && s.contains("ponta final"));
        assertThat(n.getPipes()).extracting(EpanetPipe::getId).doesNotContain("P3");
        assertThat(n.getJunctions()).extracting(EpanetJunction::getId).containsExactly("J1", "J2");
    }

    @Test
    @DisplayName("tubulação sem códigos de ponta gravados é problema e fica fora da rede")
    void pipeWithoutNodeCodesIsAnIssue() {
        List<ElementSpec> l = base();
        // Construtor antigo, sem topologia: é o dado legado que o M4 se recusa a chutar.
        l.add(new ElementSpec("P3", "P3", Kind.PIPE, line(-46.0020, -23.0, -46.0030, -23.0), 100,
                new BigDecimal("110"), "PVC", null, new HashMap<>()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).anyMatch(s -> s.contains("P3") && s.contains("sem topologia gravada"));
        assertThat(n.getPipes()).extracting(EpanetPipe::getId).containsExactly("P1", "P2");
    }

    @Test
    @DisplayName("peça referenciada por uma tubulação só é problema e fica fora, com a tubulação")
    void pieceReferencedByOnePipeOnlyIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0025, -23.0), null, null, null, null, nums()));
        l.add(pipe("P3", -46.0020, -46.0025, "55", 100, "PVC", "J2", "V1")); // nada sai de V1
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).anyMatch(s -> s.contains("V1") && s.contains("um lado só"));
        assertThat(n.getPipes()).extracting(EpanetPipe::getId).doesNotContain("P3", "V1");
        assertThat(n.getJunctions()).extracting(EpanetJunction::getId)
                .doesNotContain("V1-i", "V1-o");
    }

    @Test
    @DisplayName("duas tubulações no mesmo lado da peça são problema")
    void twoPipesOnTheSameSideOfAPieceIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V1", "V1", Kind.VALVE, pt(-46.0015, -23.0), null, null, null, null, nums()));
        l.add(pipe("P3", -46.0000, -46.0015, "160", 150, "PVC", "W1", "V1"));
        l.add(pipe("P4", -46.0010, -46.0015, "55", 150, "PVC", "J1", "V1")); // também chega como node_2
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);

        assertThat(n.getIssues()).anyMatch(s -> s.contains("V1") && s.contains("mesmo lado"));
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
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(new ElementSpec("B1", "B1", Kind.PUMP, pt(-46.0005, -23.0), null, null, null, null, nums()));
        l.add(pipe("P1", -46.0000, -46.0005, "55", 150, "PVC", "W1", "B1"));
        l.add(pipe("P2", -46.0005, -46.0010, "55", 150, "PVC", "B1", "J1"));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("B1") && s.contains("potência"));
    }

    @Test
    @DisplayName("válvula de controle sem regulagem é problema (antes assumia PRV 50)")
    void controlValveWithoutSettingIsAnIssue() {
        List<ElementSpec> l = new ArrayList<>();
        l.add(well("W1", -46.0000, 100));
        l.add(junction("J1", -46.0010, 90.0, 2.5));
        l.add(new ElementSpec("PRV1", "PRV1", Kind.VALVE, pt(-46.0005, -23.0), null, null, null,
                EpanetValve.ValveType.PRV, nums()));
        l.add(pipe("P1", -46.0000, -46.0005, "55", 150, "PVC", "W1", "PRV1"));
        l.add(pipe("P2", -46.0005, -46.0010, "55", 150, "PVC", "PRV1", "J1"));
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
    @DisplayName("válvula sem nenhuma tubulação que a referencie é problema")
    void valveNotReferencedByAnyPipeIsAnIssue() {
        List<ElementSpec> l = base();
        l.add(new ElementSpec("V9", "V9", Kind.VALVE, pt(-46.0005, -23.0100), null, null, null, null, nums()));
        EpanetNetwork n = NetworkTopologyBuilder.build("t", "d", l);
        assertThat(n.getIssues()).anyMatch(s -> s.contains("V9") && s.contains("não está ligada a nenhuma tubulação"));
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
        l.set(3, pipe("P1", -46.0000, -46.0010, null, 150, "PVC", "W1", "J1"));
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
