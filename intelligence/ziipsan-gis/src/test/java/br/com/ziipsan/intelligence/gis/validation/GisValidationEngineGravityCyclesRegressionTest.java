package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationEngine.Snapshot;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ArcFeature;
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
 * Regressão M3 (D4 da revisão da P2.21): o motor emitia no máximo um
 * {@code GRAVITY_CYCLE} por execução de {@code detect}, qualquer que
 * fosse o número de ciclos disjuntos no retrato — a sonda do revisor com
 * dois ciclos disjuntos de esgoto recebia exatamente 1 problema. O
 * contrato corrigido: um achado por ciclo disjunto; um ciclo, um achado;
 * sem ciclo, nenhum.
 */
class GisValidationEngineGravityCyclesRegressionTest {

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    private static NodeFeature manhole(int n) {
        return new NodeFeature(id(n), id(900), "MANHOLE", "MANHOLE", "SEWER", "NODE", null,
                "IN_SERVICE", null, null, null, null, false,
                "OPEN", "OPEN", new BigDecimal("10.00"), "RTK_FIXED", "WITHIN_TARGET", null);
    }

    private static ArcFeature sewerArc(int n, UUID from, UUID to) {
        return new ArcFeature(id(n), id(901), "GRAVITY_SEWER", "PIPE", "SEWER", "IN_SERVICE",
                from, to, 200, "PVC_SOLID_WALL", false, "SEWER_NETWORK", Boolean.FALSE,
                "WITH_GEOMETRY", new BigDecimal("10"), new BigDecimal("9"),
                "RTK_FIXED", "WITHIN_TARGET", null);
    }

    /** Um ciclo dirigido de 3 PVs a partir do nó-base {@code base} (arcos base*10+1..3). */
    private static void cycle(List<NodeFeature> nodes, List<ArcFeature> arcs, int base) {
        nodes.add(manhole(base));
        nodes.add(manhole(base + 1));
        nodes.add(manhole(base + 2));
        arcs.add(sewerArc(base * 10 + 1, id(base), id(base + 1)));
        arcs.add(sewerArc(base * 10 + 2, id(base + 1), id(base + 2)));
        arcs.add(sewerArc(base * 10 + 3, id(base + 2), id(base)));
    }

    private static List<DetectedIssue> gravityCycles(List<NodeFeature> nodes, List<ArcFeature> arcs) {
        return GisValidationEngine.detect(new Snapshot(nodes, arcs, List.of(), List.of(), List.of(), Set.of()))
                .stream().filter(i -> i.ruleCode().equals("GRAVITY_CYCLE")).toList();
    }

    @Test
    @DisplayName("regressão D4: dois ciclos disjuntos de esgoto geram dois GRAVITY_CYCLE")
    void twoDisjointCyclesEmitTwoFindings() {
        List<NodeFeature> nodes = new ArrayList<>();
        List<ArcFeature> arcs = new ArrayList<>();
        cycle(nodes, arcs, 1);
        cycle(nodes, arcs, 4);

        List<DetectedIssue> cycles = gravityCycles(nodes, arcs);

        assertThat(cycles).hasSize(2);
        assertThat(cycles).extracting(DetectedIssue::featureId).doesNotHaveDuplicates();
        assertThat(cycles).allSatisfy(i -> assertThat(i.severity()).isEqualTo("ERROR"));
    }

    @Test
    @DisplayName("regressão D4: três ciclos disjuntos geram três GRAVITY_CYCLE")
    void threeDisjointCyclesEmitThreeFindings() {
        List<NodeFeature> nodes = new ArrayList<>();
        List<ArcFeature> arcs = new ArrayList<>();
        cycle(nodes, arcs, 1);
        cycle(nodes, arcs, 4);
        cycle(nodes, arcs, 7);

        assertThat(gravityCycles(nodes, arcs)).hasSize(3);
    }

    @Test
    @DisplayName("um ciclo só continua gerando exatamente um GRAVITY_CYCLE")
    void singleCycleEmitsOneFinding() {
        List<NodeFeature> nodes = new ArrayList<>();
        List<ArcFeature> arcs = new ArrayList<>();
        cycle(nodes, arcs, 1);

        assertThat(gravityCycles(nodes, arcs)).hasSize(1);
    }

    @Test
    @DisplayName("cadeia acíclica de esgoto não gera GRAVITY_CYCLE")
    void acyclicChainEmitsNoFinding() {
        List<NodeFeature> nodes = List.of(manhole(1), manhole(2), manhole(3));
        List<ArcFeature> arcs = List.of(
                sewerArc(11, id(1), id(2)),
                sewerArc(12, id(2), id(3)));

        assertThat(gravityCycles(nodes, arcs)).isEmpty();
    }

    @Test
    @DisplayName("ciclo disjunto ao lado de cadeia acíclica: só o ciclo é reportado, uma vez")
    void cycleBesideAcyclicChainEmitsOneFinding() {
        List<NodeFeature> nodes = new ArrayList<>();
        List<ArcFeature> arcs = new ArrayList<>();
        cycle(nodes, arcs, 1);
        nodes.add(manhole(7));
        nodes.add(manhole(8));
        arcs.add(sewerArc(71, id(7), id(8)));

        assertThat(gravityCycles(nodes, arcs)).hasSize(1);
    }
}
