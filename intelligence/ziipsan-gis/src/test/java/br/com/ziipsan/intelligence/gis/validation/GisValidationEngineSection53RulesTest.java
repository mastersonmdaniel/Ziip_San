package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationEngine.Snapshot;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ArcFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectivityRule;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.NodeFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correção M3 (lacuna declarada da P2.21): testes de comportamento das
 * regras do §5.3 que só tinham a semente em {@code gis_connectivity_rule}
 * e o mecanismo genérico provado via CAP — contagens de grau 1/2/3/4 e
 * JUNCTION ≥ 2, tipos terminais (PLUG/BLIND_FLANGE/TERMINAL_CLEANOUT),
 * luva (COUPLING) com mesmo DN, JUNCTION com mudança de material, a
 * exceção {@code flow_exception} do PV/TIL/caixa e a boca de lobo
 * (INLET). Cada teste gera o problema esperado e o caso que passa, no
 * padrão da suíte existente (motor puro, retrato em memória).
 *
 * <p>A boca de lobo é testada no motor com o tipo sintético {@code INLET}
 * (o conjunto {@code INLET_TYPE_CODES} do motor); a semente do tipo no
 * catálogo de fábrica é da onda de drenagem, como registrado no relatório
 * da entrega — o teste prova a regra do motor, não a semente.
 */
class GisValidationEngineSection53RulesTest {

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    private static NodeFeature node(int n, String type, String clazz, String domain) {
        return new NodeFeature(id(n), id(900), type, clazz, domain, "NODE", null,
                "IN_SERVICE", null, null, null, null, false,
                "OPEN", "OPEN", new BigDecimal("10.00"), "RTK_FIXED", "WITHIN_TARGET", null);
    }

    private static NodeFeature nodeDn(int n, String type, Integer dn, Integer dn2) {
        NodeFeature base = node(n, type, "FITTING", "WATER");
        return new NodeFeature(base.id(), base.typeId(), base.typeCode(), base.classCode(), base.domain(),
                base.topologyRole(), base.terminalConfig(), base.lifecycleStatus(), base.catalogItemId(),
                dn, dn2, base.materialCode(), base.legacyOnly(), base.normalStatus(), base.operationalStatus(),
                base.invertElevationM(), base.captureMethod(), base.accuracyStatus(), base.changeSetId());
    }

    private static ArcFeature arc(int n, UUID node1, UUID node2, Integer dn, String material) {
        return new ArcFeature(id(n), id(901), "WATER_MAIN", "PIPE", "WATER", "IN_SERVICE",
                node1, node2, dn, material, false, "WATER_DISTRIBUTION", Boolean.TRUE, "UNKNOWN",
                null, null, "RTK_FIXED", "WITHIN_TARGET", null);
    }

    private static ConnectivityRule countRule(String fromType, Integer min, Integer max) {
        return new ConnectivityRule(fromType, null, null, "PIPE",
                "CONNECTS", min, max, null, "ERROR");
    }

    /** Nó central do tipo dado ligado a {@code arcCount} vizinhos JUNCTION por arcos DN 100. */
    private static Snapshot star(String centerType, int arcCount, ConnectivityRule rule) {
        NodeFeature center = node(1, centerType, "FITTING", "WATER");
        List<NodeFeature> nodes = new ArrayList<>(List.of(center));
        List<ArcFeature> arcs = new ArrayList<>();
        for (int i = 0; i < arcCount; i++) {
            NodeFeature neighbor = node(10 + i, "JUNCTION", "JUNCTION", "WATER");
            nodes.add(neighbor);
            arcs.add(arc(100 + i, center.id(), neighbor.id(), 100, "PVC_PBA"));
        }
        return new Snapshot(nodes, arcs, List.of(), List.of(), List.of(rule), Set.of());
    }

    private static List<DetectedIssue> arcCountOn(Snapshot snapshot, UUID featureId) {
        return GisValidationEngine.detect(snapshot).stream()
                .filter(i -> i.ruleCode().equals("CONNECTIVITY_ARC_COUNT"))
                .filter(i -> i.featureId().equals(featureId))
                .toList();
    }

    private static List<String> codes(List<DetectedIssue> issues) {
        return issues.stream().map(DetectedIssue::ruleCode).toList();
    }

    // ------------------------------------------------------------ contagens §5.3

    @Test
    @DisplayName("tipos terminais (PLUG, BLIND_FLANGE, TERMINAL_CLEANOUT): exatamente 1 arco")
    void terminalTypesExactlyOneArc() {
        for (String type : List.of("PLUG", "BLIND_FLANGE", "TERMINAL_CLEANOUT")) {
            ConnectivityRule rule = countRule(type, 1, 1);
            assertThat(arcCountOn(star(type, 1, rule), id(1)))
                    .as("%s com 1 arco passa", type).isEmpty();
            assertThat(arcCountOn(star(type, 2, rule), id(1)))
                    .as("%s com 2 arcos viola", type)
                    .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("ERROR"));
            assertThat(arcCountOn(star(type, 0, rule), id(1)))
                    .as("%s com 0 arcos viola", type).isNotEmpty();
        }
    }

    @Test
    @DisplayName("peças de 2 arcos (BEND, GATE_VALVE, DISMANTLING_JOINT): exatamente 2")
    void twoArcTypesExactlyTwo() {
        for (String type : List.of("BEND", "GATE_VALVE", "DISMANTLING_JOINT")) {
            ConnectivityRule rule = countRule(type, 2, 2);
            assertThat(arcCountOn(star(type, 2, rule), id(1)))
                    .as("%s com 2 arcos passa", type).isEmpty();
            assertThat(arcCountOn(star(type, 1, rule), id(1)))
                    .as("%s com 1 arco viola", type).isNotEmpty();
            assertThat(arcCountOn(star(type, 3, rule), id(1)))
                    .as("%s com 3 arcos viola", type).isNotEmpty();
        }
    }

    @Test
    @DisplayName("tê e junção em Y (TEE, WYE): exatamente 3 arcos")
    void teeAndWyeExactlyThree() {
        for (String type : List.of("TEE", "WYE")) {
            ConnectivityRule rule = countRule(type, 3, 3);
            assertThat(arcCountOn(star(type, 3, rule), id(1)))
                    .as("%s com 3 arcos passa", type).isEmpty();
            assertThat(arcCountOn(star(type, 2, rule), id(1)))
                    .as("%s com 2 arcos viola", type).isNotEmpty();
            assertThat(arcCountOn(star(type, 4, rule), id(1)))
                    .as("%s com 4 arcos viola", type).isNotEmpty();
        }
    }

    @Test
    @DisplayName("cruzeta (CROSS): exatamente 4 arcos")
    void crossExactlyFour() {
        ConnectivityRule rule = countRule("CROSS", 4, 4);
        assertThat(arcCountOn(star("CROSS", 4, rule), id(1))).isEmpty();
        assertThat(arcCountOn(star("CROSS", 3, rule), id(1))).isNotEmpty();
        assertThat(arcCountOn(star("CROSS", 5, rule), id(1))).isNotEmpty();
    }

    @Test
    @DisplayName("JUNCTION: mínimo de 2 arcos (semente ERROR); 1 arco viola, 2 e 3 passam")
    void junctionMinimumTwoArcs() {
        ConnectivityRule rule = countRule("JUNCTION", 2, null);
        assertThat(arcCountOn(star("JUNCTION", 1, rule), id(1)))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("ERROR"));
        assertThat(arcCountOn(star("JUNCTION", 2, rule), id(1))).isEmpty();
        assertThat(arcCountOn(star("JUNCTION", 3, rule), id(1))).isEmpty();
    }

    @Test
    @DisplayName("peça especial (SPECIAL_PIECE): faixa de 2 a 4 arcos da semente")
    void specialPieceRangeTwoToFour() {
        ConnectivityRule rule = countRule("SPECIAL_PIECE", 2, 4);
        assertThat(arcCountOn(star("SPECIAL_PIECE", 1, rule), id(1))).isNotEmpty();
        assertThat(arcCountOn(star("SPECIAL_PIECE", 2, rule), id(1))).isEmpty();
        assertThat(arcCountOn(star("SPECIAL_PIECE", 4, rule), id(1))).isEmpty();
        assertThat(arcCountOn(star("SPECIAL_PIECE", 5, rule), id(1))).isNotEmpty();
    }

    // ------------------------------------------------------------ condições §5.3

    @Test
    @DisplayName("luva (COUPLING): DN diferente nos dois arcos gera CONNECTIVITY_SAME_DN; igual, passa")
    void couplingSameDn() {
        NodeFeature coupling = nodeDn(1, "COUPLING", null, null);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");

        assertThat(codes(GisValidationEngine.detect(new Snapshot(
                List.of(coupling, a, b),
                List.of(arc(11, coupling.id(), a.id(), 100, "PVC_PBA"),
                        arc(12, coupling.id(), b.id(), 150, "PVC_PBA")),
                List.of(), List.of(), List.of(), Set.of()))))
                .contains("CONNECTIVITY_SAME_DN");

        assertThat(codes(GisValidationEngine.detect(new Snapshot(
                List.of(coupling, a, b),
                List.of(arc(11, coupling.id(), a.id(), 100, "PVC_PBA"),
                        arc(12, coupling.id(), b.id(), 100, "PVC_PBA")),
                List.of(), List.of(), List.of(), Set.of()))))
                .doesNotContain("CONNECTIVITY_SAME_DN");
    }

    @Test
    @DisplayName("JUNCTION com mudança de material sem peça (mesmo DN) é WARNING")
    void junctionMaterialChangeIsWarning() {
        NodeFeature junction = node(1, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        List<DetectedIssue> issues = GisValidationEngine.detect(new Snapshot(
                List.of(junction, a, b),
                List.of(arc(11, junction.id(), a.id(), 100, "PVC_PBA"),
                        arc(12, junction.id(), b.id(), 100, "PE100")),
                List.of(), List.of(), List.of(), Set.of()));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("DN_MISMATCH_WITHOUT_FITTING"))
                .filteredOn(i -> i.featureId().equals(junction.id()))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("WARNING"));
    }

    @Test
    @DisplayName("PV com flow_exception no terminal_config é dispensado da regra de entrada/saída; sem a exceção, não")
    void manholeFlowExceptionExempts() {
        NodeFeature exempt = new NodeFeature(id(1), id(900), "MANHOLE", "MANHOLE", "SEWER", "NODE",
                "{\"flow_exception\": true}",
                "IN_SERVICE", null, null, null, null, false,
                "OPEN", "OPEN", new BigDecimal("10.00"), "RTK_FIXED", "WITHIN_TARGET", null);
        NodeFeature plain = node(2, "MANHOLE", "MANHOLE", "SEWER");
        NodeFeature feeder = node(3, "MANHOLE", "MANHOLE", "SEWER");
        // Os dois PVs recebem um arco e não têm saída: só o sem exceção viola.
        ArcFeature inExempt = new ArcFeature(id(11), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                feeder.id(), exempt.id(), 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("9"), "RTK_FIXED", "WITHIN_TARGET", null);
        ArcFeature inPlain = new ArcFeature(id(12), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                feeder.id(), plain.id(), 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("9"), "RTK_FIXED", "WITHIN_TARGET", null);
        List<DetectedIssue> issues = GisValidationEngine.detect(new Snapshot(
                List.of(exempt, plain, feeder), List.of(inExempt, inPlain),
                List.of(), List.of(), List.of(), Set.of()));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("MANHOLE_FLOW"))
                .extracting(DetectedIssue::featureId)
                .contains(plain.id())
                .doesNotContain(exempt.id());
    }

    @Test
    @DisplayName("boca de lobo (INLET): sem arco para PV pluvial ou galeria é erro; ligada a PV, passa")
    void inletMustLeadToManholeOrGallery() {
        NodeFeature inlet = node(1, "INLET", "INLET", "STORM");
        NodeFeature junction = node(2, "JUNCTION", "JUNCTION", "STORM");
        ArcFeature toJunction = new ArcFeature(id(11), id(901), "INLET_LEAD", "PIPE", "STORM", "IN_SERVICE",
                inlet.id(), junction.id(), 300, "CONCRETE", false, "STORM_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("9"), "RTK_FIXED", "WITHIN_TARGET", null);
        assertThat(GisValidationEngine.detect(new Snapshot(
                List.of(inlet, junction), List.of(toJunction), List.of(), List.of(), List.of(), Set.of())))
                .filteredOn(i -> i.ruleCode().equals("INLET_NOT_CONNECTED"))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("ERROR"));

        NodeFeature manhole = node(3, "MANHOLE", "MANHOLE", "STORM");
        ArcFeature toManhole = new ArcFeature(id(12), id(901), "INLET_LEAD", "PIPE", "STORM", "IN_SERVICE",
                inlet.id(), manhole.id(), 300, "CONCRETE", false, "STORM_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("9"), "RTK_FIXED", "WITHIN_TARGET", null);
        assertThat(codes(GisValidationEngine.detect(new Snapshot(
                List.of(inlet, manhole), List.of(toManhole), List.of(), List.of(), List.of(), Set.of()))))
                .doesNotContain("INLET_NOT_CONNECTED");
    }
}
