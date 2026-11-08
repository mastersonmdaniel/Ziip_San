package br.com.ziipsan.intelligence.gis.hydraulic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwmmInpWriterTest {

    @Test
    void secoesDoAceitePresentes() {
        String inp = SwmmInpWriter.write(HydraulicFixtures.sampleSewer());
        for (String sec : List.of("[JUNCTIONS]", "[OUTFALLS]", "[STORAGE]", "[CONDUITS]", "[XSECTIONS]",
                "[SUBCATCHMENTS]", "[SUBAREAS]", "[INFILTRATION]", "[INLETS]", "[INLET_USAGE]", "[REPORT]")) {
            assertTrue(inp.contains(sec), sec);
        }
    }

    @Test
    void semSecaoEndQueOSwmmRecusa() {
        // SWMM 5.2: [END] gera ERROR 205 (conferido executando a biblioteca real).
        org.junit.jupiter.api.Assertions.assertFalse(SwmmInpWriter.write(HydraulicFixtures.sampleSewer()).contains("[END]"));
    }

    @Test
    void juncaoProfundidadeTampaMenosFundoECondutoComManningDoMaterial() {
        String inp = SwmmInpWriter.write(HydraulicFixtures.sampleSewer());
        assertTrue(inp.contains("PV1\t10\t2.5\t0\t0\t0"), inp); // fundo 10, tampa 12,5 -> prof 2,5
        assertTrue(inp.contains("C1\tPV1\tPV2\t100\t0.013"), inp); // CONCRETE do catalogo de fabrica
        assertTrue(inp.contains("C3\tPV2\tDET1\t30\t0.01"), inp); // PVC_SOLID_WALL
        assertTrue(inp.contains("C1\tCIRCULAR\t0.6"), inp);
    }

    @Test
    void armazenamentoOutfallSubBaciaEBocaDeLobo() {
        String inp = SwmmInpWriter.write(HydraulicFixtures.sampleSewer());
        assertTrue(inp.contains("DET1\t8.5\t3\t0\tFUNCTIONAL\t0\t0\t400\t0"), inp);
        assertTrue(inp.contains("OUT1\t8\tFREE"), inp);
        assertTrue(inp.contains("SB1\tRG1\tPV1\t2.5\t60\t120\t1.5"), inp);
        assertTrue(inp.contains("BL1\tGRATE\t0.9\t0.6\tGENERIC\t0.5"), inp);
        assertTrue(inp.contains("C1\tBL1\tPV1\t1\t0\t0"), inp);
    }

    @Test
    void materialSemManningEhProblema() {
        var base = HydraulicFixtures.sampleSewer();
        var s = new HydraulicModel.SewerSnapshot(base.request(), base.title(), base.nodes(), base.arcs(),
                base.subcatchments(), base.inlets(), Map.of());
        var ex = assertThrows(HydraulicValidationException.class, () -> SwmmInpWriter.write(s));
        assertTrue(ex.getMessage().contains("sem Manning"), ex.getMessage());
    }
}
