package br.com.ziipsan.intelligence.gis.hydraulic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpanetInpWriterTest {

    @Test
    void demandaVemDoConsumoFaturado() {
        var s = HydraulicFixtures.analyticWater();
        // 51.840 m3 em 30 dias = 20 L/s exatos
        assertEquals(0, new BigDecimal("20").compareTo(s.demandLps("J1")));
        String inp = EpanetInpWriter.write(s);
        assertTrue(inp.contains("J1\t10\t20"), inp);
    }

    @Test
    void rugosidadeVemDoMaterialDoCatalogo() {
        String inp = EpanetInpWriter.write(HydraulicFixtures.sampleWater());
        assertTrue(inp.contains("P1\tR1\tJ1\t1000\t300\t140\t0\tOpen"), inp);
        assertTrue(inp.contains("P2\tT1\tJ2\t800\t200\t130\t0\tOpen"), inp);
    }

    @Test
    void secoesCompletasEReportParaLeituraDoResultado() {
        String inp = EpanetInpWriter.write(HydraulicFixtures.sampleWater());
        for (String sec : List.of("[JUNCTIONS]", "[RESERVOIRS]", "[TANKS]", "[PIPES]", "[PUMPS]", "[VALVES]",
                "[OPTIONS]", "[TIMES]", "[REPORT]", "[COORDINATES]", "[END]")) {
            assertTrue(inp.contains(sec), sec);
        }
        assertTrue(inp.contains("Nodes\t\tAll") && inp.contains("Links\t\tAll"));
        assertTrue(inp.contains("B1\tJ1\tT1\tPOWER 15"), inp);
        assertTrue(inp.contains("V1\tJ1\tJ2\t200\tPRV\t30\t0"), inp);
        assertTrue(inp.contains("zona=Z1") && inp.contains("data=2026-01-31"), inp);
    }

    @Test
    void materialSemRugosidadeEhProblemaNuncaPadraoSilencioso() {
        var base = HydraulicFixtures.analyticWater();
        var semC = new HydraulicModel.WaterSnapshot(base.request(), base.title(), base.nodes(), base.arcs(),
                base.billedVolumeM3ByNode(), base.billedPeriodDays(), Map.of(), List.of());
        var ex = assertThrows(HydraulicValidationException.class, () -> EpanetInpWriter.write(semC));
        assertTrue(ex.getMessage().contains("sem rugosidade"), ex.getMessage());
    }

    @Test
    void noSemCotaEhProblema() {
        var base = HydraulicFixtures.analyticWater();
        var nodes = List.of(base.nodes().get(0),
                new HydraulicModel.WaterNode("J1", "x", HydraulicModel.WaterNode.WaterNodeKind.JUNCTION,
                        null, null, null, null, null, null, null, null));
        var s = new HydraulicModel.WaterSnapshot(base.request(), "t", nodes, base.arcs(),
                base.billedVolumeM3ByNode(), 30, base.materialHwC(), List.of());
        assertThrows(HydraulicValidationException.class, () -> EpanetInpWriter.write(s));
    }
}
