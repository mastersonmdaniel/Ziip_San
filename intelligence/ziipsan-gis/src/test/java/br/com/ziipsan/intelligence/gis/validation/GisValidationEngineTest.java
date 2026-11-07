package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationEngine.Snapshot;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ArcFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectivityRule;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectionFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.NodeFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GisValidationEngine: cada regra do §5.3 e do §5.4 gera o problema
 * esperado, com dados em memória (aceite da GIS-03: cada regra com teste
 * que gera o problema esperado). Os construtores abaixo fixam os campos
 * irrelevantes para a regra em teste em valores válidos.
 */
class GisValidationEngineTest {

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

    private static ArcFeature arc(int n, UUID node1, UUID node2, Integer dn, String function) {
        return new ArcFeature(id(n), id(901), "WATER_MAIN", "PIPE", "WATER", "IN_SERVICE",
                node1, node2, dn, "PVC_PBA", false, function, Boolean.TRUE, "UNKNOWN",
                null, null, "RTK_FIXED", "WITHIN_TARGET", null);
    }

    private static Snapshot snapshot(List<NodeFeature> nodes, List<ArcFeature> arcs) {
        return new Snapshot(nodes, arcs, List.of(), List.of(), List.of(), Set.of());
    }

    private static List<String> codes(List<DetectedIssue> issues) {
        return issues.stream().map(DetectedIssue::ruleCode).toList();
    }

    // ------------------------------------------------------------ §5.3

    @Test
    @DisplayName("contagem: cap com dois arcos viola a regra de exatamente 1")
    void arcCountFromRuleIsEnforced() {
        NodeFeature cap = node(1, "CAP", "FITTING", "WATER");
        NodeFeature j = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature k = node(3, "JUNCTION", "JUNCTION", "WATER");
        ConnectivityRule rule = new ConnectivityRule("CAP", null, null, "PIPE",
                "CONNECTS", 1, 1, null, "ERROR");
        List<DetectedIssue> issues = GisValidationEngine.detect(new Snapshot(
                List.of(cap, j, k),
                List.of(arc(11, cap.id(), j.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, cap.id(), k.id(), 100, "WATER_DISTRIBUTION")),
                List.of(), List.of(), List.of(rule), Set.of()));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("CONNECTIVITY_ARC_COUNT"))
                .singleElement().satisfies(i -> {
                    assertThat(i.featureId()).isEqualTo(cap.id());
                    assertThat(i.severity()).isEqualTo("ERROR");
                });
    }

    @Test
    @DisplayName("condition da semente de fábrica: {dn:SAME} na regra gera o mesmo problema da curva")
    void factoryConditionVocabularyIsHonored() {
        NodeFeature bend = nodeDn(1, "BEND", null, null);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        ConnectivityRule rule = new ConnectivityRule("BEND", null, null, "PIPE",
                "CONNECTS", null, null, "{\"dn\": \"SAME\"}", "ERROR");
        List<DetectedIssue> issues = GisValidationEngine.detect(new Snapshot(
                List.of(bend, a, b),
                List.of(arc(11, bend.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, bend.id(), b.id(), 150, "WATER_DISTRIBUTION")),
                List.of(), List.of(), List.of(rule), Set.of()));

        // Um problema só: a via da regra e o check próprio por tipo têm o
        // mesmo código e a chave deduplica.
        assertThat(issues).filteredOn(i -> i.ruleCode().equals("CONNECTIVITY_SAME_DN"))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("ERROR"));
    }

    @Test
    @DisplayName("curva com DN diferente nos dois arcos gera CONNECTIVITY_SAME_DN")
    void bendWithDifferentDnIsFlagged() {
        NodeFeature bend = nodeDn(1, "BEND", null, null);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        List<DetectedIssue> issues = GisValidationEngine.detect(snapshot(
                List.of(bend, a, b),
                List.of(arc(11, bend.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, bend.id(), b.id(), 150, "WATER_DISTRIBUTION"))));

        assertThat(codes(issues)).contains("CONNECTIVITY_SAME_DN");
    }

    @Test
    @DisplayName("redução: DNs dos arcos iguais a DN e DN2 do item passam; diferentes, não")
    void reducerDnMustMatchItem() {
        NodeFeature reducer = nodeDn(1, "REDUCER", 150, 100);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");

        assertThat(codes(GisValidationEngine.detect(snapshot(List.of(reducer, a, b),
                List.of(arc(11, reducer.id(), a.id(), 150, "WATER_DISTRIBUTION"),
                        arc(12, reducer.id(), b.id(), 100, "WATER_DISTRIBUTION"))))))
                .doesNotContain("CONNECTIVITY_REDUCER_DN");

        assertThat(codes(GisValidationEngine.detect(snapshot(List.of(reducer, a, b),
                List.of(arc(11, reducer.id(), a.id(), 150, "WATER_DISTRIBUTION"),
                        arc(12, reducer.id(), b.id(), 150, "WATER_DISTRIBUTION"))))))
                .contains("CONNECTIVITY_REDUCER_DN");
    }

    @Test
    @DisplayName("junction com mudança de DN sem peça é WARNING (§5.3, não o ERROR do §5.4)")
    void junctionChangeIsWarning() {
        NodeFeature junction = node(1, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        List<DetectedIssue> issues = GisValidationEngine.detect(snapshot(
                List.of(junction, a, b),
                List.of(arc(11, junction.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, junction.id(), b.id(), 200, "WATER_DISTRIBUTION"))));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("DN_MISMATCH_WITHOUT_FITTING"))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("WARNING"));
    }

    @Test
    @DisplayName("ramal em adutora é ERROR e em subadutora é WARNING")
    void connectionToTransmissionSeverities() {
        NodeFeature a = node(1, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(2, "JUNCTION", "JUNCTION", "WATER");
        ArcFeature main = arc(11, a.id(), b.id(), 300, "TREATED_WATER_MAIN");
        ArcFeature sub = arc(12, a.id(), b.id(), 200, "SUBMAIN");
        ConnectionFeature onMain = new ConnectionFeature(id(21), "WATER_SERVICE_LINE", "SERVICE_LINE",
                "WATER", "IN_SERVICE", main.id(), null, id(31), 20, "PE100", false,
                "RTK_FIXED", "WITHIN_TARGET", null);
        ConnectionFeature onSub = new ConnectionFeature(id(22), "WATER_SERVICE_LINE", "SERVICE_LINE",
                "WATER", "IN_SERVICE", sub.id(), null, id(32), 20, "PE100", false,
                "RTK_FIXED", "WITHIN_TARGET", null);
        List<DetectedIssue> issues = GisValidationEngine.detect(new Snapshot(
                List.of(a, b), List.of(main, sub), List.of(onMain, onSub), List.of(), List.of(), Set.of()));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("CONNECTIVITY_ARC_COUNT")).isEmpty();
        assertThat(issues).filteredOn(i -> i.ruleCode().equals("CONNECTION_TO_TRANSMISSION"))
                .extracting(DetectedIssue::severity).containsExactlyInAnyOrder("ERROR", "WARNING");
    }

    @Test
    @DisplayName("hidrante sem ramal de hidrante nem tê é erro; com tê, passa")
    void hydrantNeedsBranchOrTee() {
        NodeFeature hydrant = node(1, "HYDRANT_PILLAR", "HYDRANT", "WATER");
        NodeFeature junction = node(2, "JUNCTION", "JUNCTION", "WATER");
        assertThat(codes(GisValidationEngine.detect(snapshot(List.of(hydrant, junction),
                List.of(arc(11, hydrant.id(), junction.id(), 100, "WATER_DISTRIBUTION"))))))
                .contains("HYDRANT_NOT_CONNECTED");

        NodeFeature tee = nodeDn(3, "TEE", null, null);
        assertThat(codes(GisValidationEngine.detect(snapshot(List.of(hydrant, tee),
                List.of(arc(11, hydrant.id(), tee.id(), 100, "WATER_DISTRIBUTION"))))))
                .doesNotContain("HYDRANT_NOT_CONNECTED");
    }

    @Test
    @DisplayName("domínio diferente entre arco e nó é erro, salvo nó de interface")
    void domainMismatchExceptInterface() {
        NodeFeature sewerNode = node(1, "MANHOLE", "MANHOLE", "SEWER");
        NodeFeature waterNode = node(2, "JUNCTION", "JUNCTION", "WATER");
        ArcFeature waterArc = arc(11, sewerNode.id(), waterNode.id(), 100, "WATER_DISTRIBUTION");
        assertThat(codes(GisValidationEngine.detect(snapshot(List.of(sewerNode, waterNode), List.of(waterArc)))))
                .contains("DOMAIN_MISMATCH");

        NodeFeature overflow = node(3, "OVERFLOW", "OUTFALL", "SEWER");
        ArcFeature arcToOverflow = arc(12, overflow.id(), waterNode.id(), 100, "WATER_DISTRIBUTION");
        assertThat(codes(GisValidationEngine.detect(snapshot(List.of(overflow, waterNode), List.of(arcToOverflow)))))
                .doesNotContain("DOMAIN_MISMATCH");
    }

    @Test
    @DisplayName("PV sem saída gera MANHOLE_FLOW; com entrada e saída, passa")
    void manholeNeedsInflowAndOutflow() {
        NodeFeature pv1 = node(1, "MANHOLE", "MANHOLE", "SEWER");
        NodeFeature pv2 = node(2, "MANHOLE", "MANHOLE", "SEWER");
        ArcFeature sewer = new ArcFeature(id(11), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                pv1.id(), pv2.id(), 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("12"), new BigDecimal("11"), "RTK_FIXED", "WITHIN_TARGET", null);
        List<DetectedIssue> issues = GisValidationEngine.detect(snapshot(List.of(pv1, pv2), List.of(sewer)));

        // Regra literal do §5.3: entrada E saída; os dois PVs de ponta
        // falham (um sem entrada, outro sem saída) — a exceção para PV
        // terminal é a configurável por tipo (terminal_config).
        assertThat(issues).filteredOn(i -> i.ruleCode().equals("MANHOLE_FLOW"))
                .extracting(DetectedIssue::featureId).containsExactlyInAnyOrder(pv1.id(), pv2.id());
    }

    @Test
    @DisplayName("tê (I3): ramal com DN igual ao DN2 e passantes com DN do item passam")
    void teeBranchDnMatchesItem() {
        NodeFeature tee = nodeDn(1, "TEE", 100, 50);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature c = node(4, "JUNCTION", "JUNCTION", "WATER");
        List<DetectedIssue> issues = GisValidationEngine.detect(snapshot(
                List.of(tee, a, b, c),
                List.of(arc(11, tee.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, tee.id(), b.id(), 100, "WATER_DISTRIBUTION"),
                        arc(13, tee.id(), c.id(), 50, "WATER_DISTRIBUTION"))));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("TEE_BRANCH_DN_MISMATCH")).isEmpty();
    }

    @Test
    @DisplayName("tê (I3): ramal com DN diferente do DN2 gera TEE_BRANCH_DN_MISMATCH ERROR")
    void teeBranchDnMismatchIsFlagged() {
        NodeFeature tee = nodeDn(1, "TEE", 100, 50);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature c = node(4, "JUNCTION", "JUNCTION", "WATER");
        List<DetectedIssue> issues = GisValidationEngine.detect(snapshot(
                List.of(tee, a, b, c),
                List.of(arc(11, tee.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, tee.id(), b.id(), 100, "WATER_DISTRIBUTION"),
                        arc(13, tee.id(), c.id(), 75, "WATER_DISTRIBUTION"))));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("TEE_BRANCH_DN_MISMATCH"))
                .singleElement().satisfies(i -> {
                    assertThat(i.severity()).isEqualTo("ERROR");
                    assertThat(i.featureId()).isEqualTo(tee.id());
                });
    }

    @Test
    @DisplayName("tê (I3): passantes com DN diferente do DN do item também geram o problema")
    void teeThroughDnMismatchIsFlagged() {
        NodeFeature tee = nodeDn(1, "TEE", 150, 50);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature c = node(4, "JUNCTION", "JUNCTION", "WATER");
        assertThat(codes(GisValidationEngine.detect(snapshot(
                List.of(tee, a, b, c),
                List.of(arc(11, tee.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, tee.id(), b.id(), 100, "WATER_DISTRIBUTION"),
                        arc(13, tee.id(), c.id(), 50, "WATER_DISTRIBUTION"))))))
                .contains("TEE_BRANCH_DN_MISMATCH");
    }

    @Test
    @DisplayName("tê (I3): item sem DN2 não dispara o check (sem dado, sem problema)")
    void teeWithoutDn2IsNotFlagged() {
        NodeFeature tee = nodeDn(1, "TEE", 100, null);
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature c = node(4, "JUNCTION", "JUNCTION", "WATER");
        assertThat(codes(GisValidationEngine.detect(snapshot(
                List.of(tee, a, b, c),
                List.of(arc(11, tee.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, tee.id(), b.id(), 100, "WATER_DISTRIBUTION"),
                        arc(13, tee.id(), c.id(), 75, "WATER_DISTRIBUTION"))))))
                .doesNotContain("TEE_BRANCH_DN_MISMATCH");
    }

    @Test
    @DisplayName("message (M6): todo problema detectado carrega a mensagem formulada de regra + feição")
    void detectedIssuesCarryMessage() {
        NodeFeature junction = node(1, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature a = node(2, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(3, "JUNCTION", "JUNCTION", "WATER");
        List<DetectedIssue> issues = GisValidationEngine.detect(snapshot(
                List.of(junction, a, b),
                List.of(arc(11, junction.id(), a.id(), 100, "WATER_DISTRIBUTION"),
                        arc(12, junction.id(), b.id(), 200, "WATER_DISTRIBUTION"))));

        assertThat(issues).isNotEmpty();
        assertThat(issues).allSatisfy(i -> {
            assertThat(i.message()).isNotBlank();
            assertThat(i.message()).contains(i.ruleCode());
        });
    }

    // ------------------------------------------------------------ §5.4

    @Test
    @DisplayName("nó solto, arco sem nó e válvula sem estado")
    void looseNodeArcWithoutNodeAndValveState() {
        NodeFeature loose = node(1, "GATE_VALVE", "VALVE", "WATER");
        NodeFeature noState = new NodeFeature(id(2), id(900), "GATE_VALVE", "VALVE", "WATER", "NODE", null,
                "IN_SERVICE", null, null, null, null, false, null, null,
                new BigDecimal("10"), "RTK_FIXED", "WITHIN_TARGET", null);
        NodeFeature other = node(3, "JUNCTION", "JUNCTION", "WATER");
        ArcFeature orphan = arc(11, other.id(), null, 100, "WATER_DISTRIBUTION");
        ArcFeature link = arc(12, noState.id(), other.id(), 100, "WATER_DISTRIBUTION");
        List<String> found = codes(GisValidationEngine.detect(
                snapshot(List.of(loose, noState, other), List.of(orphan, link))));

        assertThat(found).contains("NODE_WITHOUT_ARC", "ARC_WITHOUT_NODE", "VALVE_WITHOUT_STATE");
    }

    @Test
    @DisplayName("ilha sem fonte é WARNING; componente com reservatório não é ilha")
    void islandWithoutSource() {
        NodeFeature a = node(1, "JUNCTION", "JUNCTION", "WATER");
        NodeFeature b = node(2, "JUNCTION", "JUNCTION", "WATER");
        assertThat(GisValidationEngine.detect(snapshot(List.of(a, b),
                List.of(arc(11, a.id(), b.id(), 100, "WATER_DISTRIBUTION")))))
                .filteredOn(i -> i.ruleCode().equals("ISLAND_WITHOUT_SOURCE"))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("WARNING"));

        NodeFeature reservoir = node(3, "RESERVOIR", "STORAGE", "WATER");
        NodeFeature c = node(4, "JUNCTION", "JUNCTION", "WATER");
        // Reservatório é estrutura: o nó-fonte entra por contenção no retrato.
        assertThat(codes(GisValidationEngine.detect(new Snapshot(
                List.of(reservoir, c),
                List.of(arc(12, reservoir.id(), c.id(), 100, "WATER_DISTRIBUTION")),
                List.of(), List.of(), List.of(), Set.of(reservoir.id())))))
                .doesNotContain("ISLAND_WITHOUT_SOURCE");
    }

    @Test
    @DisplayName("declividade negativa e ciclo em rede por gravidade são ERROR")
    void negativeSlopeAndGravityCycle() {
        NodeFeature p1 = node(1, "MANHOLE", "MANHOLE", "SEWER");
        NodeFeature p2 = node(2, "MANHOLE", "MANHOLE", "SEWER");
        NodeFeature p3 = node(3, "MANHOLE", "MANHOLE", "SEWER");
        ArcFeature up = new ArcFeature(id(11), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                p1.id(), p2.id(), 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("11"), "RTK_FIXED", "WITHIN_TARGET", null);
        ArcFeature ok = new ArcFeature(id(12), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                p2.id(), p3.id(), 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("11"), new BigDecimal("10"), "RTK_FIXED", "WITHIN_TARGET", null);
        ArcFeature back = new ArcFeature(id(13), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                p3.id(), p1.id(), 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("9"), "RTK_FIXED", "WITHIN_TARGET", null);
        List<String> found = codes(GisValidationEngine.detect(snapshot(List.of(p1, p2, p3), List.of(up, ok, back))));

        assertThat(found).contains("NEGATIVE_SLOPE", "GRAVITY_CYCLE");
    }

    @Test
    @DisplayName("PV sem cota de fundo e ramal sem ligação são WARNING")
    void manholeInvertAndConnectionLink() {
        NodeFeature pv = new NodeFeature(id(1), id(900), "MANHOLE", "MANHOLE", "SEWER", "NODE", null,
                "IN_SERVICE", null, null, null, null, false, "OPEN", "OPEN",
                null, "RTK_FIXED", "WITHIN_TARGET", null);
        ConnectionFeature orphan = new ConnectionFeature(id(2), "SEWER_SERVICE_LINE", "SERVICE_LINE",
                "SEWER", "IN_SERVICE", null, null, null, 100, "PVC_SOLID_WALL", false,
                "RTK_FIXED", "WITHIN_TARGET", null);
        List<DetectedIssue> issues = GisValidationEngine.detect(new Snapshot(
                List.of(pv), List.of(), List.of(orphan), List.of(), List.of(), Set.of()));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("MANHOLE_WITHOUT_INVERT"))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("WARNING"));
        assertThat(issues).filteredOn(i -> i.ruleCode().equals("CONNECTION_WITHOUT_LINK"))
                .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo("WARNING"));
    }

    @Test
    @DisplayName("captura ausente é ERROR, exatidão fora da meta é WARNING, material legado em feição nova é ERROR")
    void captureAccuracyAndLegacyMaterial() {
        NodeFeature noCapture = new NodeFeature(id(1), id(900), "JUNCTION", "JUNCTION", "WATER", "NODE", null,
                "PLANNED", null, null, null, null, false, "OPEN", "OPEN",
                new BigDecimal("10"), null, "WITHIN_TARGET", null);
        NodeFeature offTarget = new NodeFeature(id(2), id(900), "JUNCTION", "JUNCTION", "WATER", "NODE", null,
                "PLANNED", null, null, null, null, false, "OPEN", "OPEN",
                new BigDecimal("10"), "RTK_FIXED", "OUT_OF_TARGET", null);
        NodeFeature legacyNew = new NodeFeature(id(3), id(900), "JUNCTION", "JUNCTION", "WATER", "NODE", null,
                "PLANNED", null, null, null, "ASBESTOS_CEMENT", true, "OPEN", "OPEN",
                new BigDecimal("10"), "RTK_FIXED", "WITHIN_TARGET", null);
        NodeFeature legacyImported = new NodeFeature(id(4), id(900), "JUNCTION", "JUNCTION", "WATER", "NODE", null,
                "PLANNED", null, null, null, "ASBESTOS_CEMENT", true, "OPEN", "OPEN",
                new BigDecimal("10"), "CAD_IMPORT", "APPROXIMATE", null);
        List<DetectedIssue> issues = GisValidationEngine.detect(
                snapshot(List.of(noCapture, offTarget, legacyNew, legacyImported), List.of()));

        assertThat(issues).filteredOn(i -> i.ruleCode().equals("MISSING_CAPTURE_METADATA"))
                .extracting(DetectedIssue::featureId).containsExactly(noCapture.id());
        assertThat(issues).filteredOn(i -> i.ruleCode().equals("ACCURACY_OUT_OF_TARGET"))
                .extracting(DetectedIssue::featureId).containsExactly(offTarget.id());
        assertThat(issues).filteredOn(i -> i.ruleCode().equals("LEGACY_MATERIAL_ON_NEW_FEATURE"))
                .extracting(DetectedIssue::featureId).containsExactly(legacyNew.id());
    }
}
