package br.com.ziipsan.intelligence.gis.drain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conectividade §5.3/§5.4 sobre uma rede sintética de resultado
 * conhecido: BL-1 →(INLET_LEAD) PV-1 →(STORM_PIPE) EX-1 (OUTFALL);
 * BL-2 sem ligação; trecho em ciclo A→B→A.
 */
class GisDrainGraphTest {

    private static GisDrainGraph graph() {
        return new GisDrainGraph(
                List.of(
                        new GisDrainGraph.DrainNode("BL-1", "INLET"),
                        new GisDrainGraph.DrainNode("BL-2", "INLET"),
                        new GisDrainGraph.DrainNode("PV-1", "STORM_MANHOLE"),
                        new GisDrainGraph.DrainNode("EX-1", "OUTFALL")),
                List.of(
                        new GisDrainGraph.DirectedArc("L-1", "INLET_LEAD", "BL-1", "PV-1"),
                        new GisDrainGraph.DirectedArc("G-1", "STORM_PIPE", "PV-1", "EX-1")));
    }

    @Test
    void bocaDeLoboSemLeadEListada() {
        assertThat(graph().inletsWithoutLead()).containsExactly("BL-2");
    }

    @Test
    void leadParaPvPluvialEValido() {
        assertThat(graph().leadsToInvalidTarget()).isEmpty();
    }

    @Test
    void jusanteEExutorioAlcancavel() {
        GisDrainGraph g = graph();
        assertThat(g.downstreamOf("BL-1")).containsExactlyInAnyOrder("BL-1", "PV-1", "EX-1");
        assertThat(g.reachesOutfall("BL-1")).isTrue();
        assertThat(g.reachesOutfall("BL-2")).isFalse();
        assertThat(g.nodesWithoutOutfall()).containsExactly("BL-2");
        assertThat(g.hasCycle()).isFalse();
    }

    @Test
    void cicloEmRedeDeGravidadeEDetectado() {
        GisDrainGraph cyclic = new GisDrainGraph(
                List.of(new GisDrainGraph.DrainNode("A", "STORM_MANHOLE"),
                        new GisDrainGraph.DrainNode("B", "STORM_MANHOLE")),
                List.of(new GisDrainGraph.DirectedArc("G-1", "STORM_PIPE", "A", "B"),
                        new GisDrainGraph.DirectedArc("G-2", "STORM_PIPE", "B", "A")));
        assertThat(cyclic.hasCycle()).isTrue();
    }
}
