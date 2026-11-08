package br.com.ziipsan.intelligence.gis.hydraulic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GIS-12: ida e volta preserva o código das feições (EPANET e SWMM). */
class InpRoundTripTest {

    @Test
    void idaEVoltaEpanetPreservaCodigos() {
        var s = HydraulicFixtures.sampleWater();
        String inp = EpanetInpWriter.write(s);
        var pkg = InpImporter.importEpanet(inp, "EPSG:31982");
        assertTrue(pkg.issues().isEmpty(), pkg.issues().toString());
        assertEquals(List.of("J1", "J2", "R1", "T1", "P1", "P2", "B1", "V1").stream().sorted().toList(),
                pkg.codes().stream().sorted().toList());
        var pipe = pkg.features().stream().filter(f -> f.code().equals("P1")).findFirst().orElseThrow();
        assertEquals("140", pipe.fields().get("roughness"));
        assertEquals("R1", pipe.fields().get("node1"));
    }

    @Test
    void idaEVoltaSwmmPreservaCodigos() {
        var s = HydraulicFixtures.sampleSewer();
        String inp = SwmmInpWriter.write(s);
        var pkg = InpImporter.importSwmm(inp, "EPSG:31982");
        assertTrue(pkg.issues().isEmpty(), pkg.issues().toString());
        assertEquals(List.of("PV1", "PV2", "DET1", "OUT1", "C1", "C2", "C3", "SB1", "BL1").stream().sorted().toList(),
                pkg.codes().stream().sorted().toList());
    }

    @Test
    void importacaoSemSrsDeclaradoRegistraProblema() {
        var pkg = InpImporter.importEpanet(EpanetInpWriter.write(HydraulicFixtures.analyticWater()), null);
        assertTrue(pkg.issues().stream().anyMatch(i -> i.contains("SRS")), pkg.issues().toString());
    }

    @Test
    void importaNet1MinimoReconhecendoFeicoes() {
        String net = """
                [JUNCTIONS]
                JX 100 10
                [RESERVOIRS]
                RX 120
                [PIPES]
                PX RX JX 1000 300 140 0 Open
                [END]
                """;
        var pkg = InpImporter.importEpanet(net, "EPSG:4674");
        assertEquals(List.of("JX", "RX", "PX"), pkg.codes());
    }
}
