package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.Inlet;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SewerArc;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SewerNode;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SewerSnapshot;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SnapshotRequest;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.Subcatchment;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterArc;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterNode;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Massas sintéticas declaradas dos testes (nenhum dado real). */
final class HydraulicFixtures {

    private HydraulicFixtures() {
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    static SnapshotRequest request() {
        return new SnapshotRequest("Z1", Set.of("IN_SERVICE"), LocalDate.of(2026, 1, 31));
    }

    /**
     * Rede analítica de resultado conhecido: reservatório (carga 60 m) →
     * tubo L=1000 m, D=300 mm, C=140 (PVC_PBA do catálogo de fábrica) →
     * junção cota 10 m com demanda de 20 L/s (consumo faturado de
     * 51.840 m³ em 30 dias). Perda de Hazen-Williams e pressões esperadas
     * calculadas pela fórmula no teste do solver.
     */
    static WaterSnapshot analyticWater() {
        var nodes = List.of(
                new WaterNode("R1", "Reservatorio", WaterNode.WaterNodeKind.RESERVOIR,
                        null, bd("60"), null, null, null, null, bd("0"), bd("0")),
                new WaterNode("J1", "Juncao", WaterNode.WaterNodeKind.JUNCTION,
                        bd("10"), null, null, null, null, null, bd("1000"), bd("0")));
        var arcs = List.of(new WaterArc("P1", "Adutora", WaterArc.WaterArcKind.PIPE, "R1", "J1",
                bd("1000"), 300, "PVC_PBA", null, null, null, null, true));
        return new WaterSnapshot(request(), "Rede analitica", nodes, arcs,
                Map.of("J1", bd("51840")), 30, Map.of("PVC_PBA", bd("140")), List.of());
    }

    /** Rede de água maior, para escritores/cenários/calibração: tanque, bomba, válvula. */
    static WaterSnapshot sampleWater() {
        var nodes = List.of(
                new WaterNode("R1", "Captação", WaterNode.WaterNodeKind.RESERVOIR,
                        null, bd("60"), null, null, null, null, bd("0"), bd("0")),
                new WaterNode("T1", "Reservatorio elevado", WaterNode.WaterNodeKind.TANK,
                        bd("40"), null, bd("3"), bd("0.5"), bd("5"), bd("10"), bd("500"), bd("500")),
                new WaterNode("J1", "Juncao 1", WaterNode.WaterNodeKind.JUNCTION,
                        bd("10"), null, null, null, null, null, bd("100"), bd("0")),
                new WaterNode("J2", "Juncao 2", WaterNode.WaterNodeKind.JUNCTION,
                        bd("12"), null, null, null, null, null, bd("200"), bd("0")));
        var arcs = List.of(
                new WaterArc("P1", "Adutora", WaterArc.WaterArcKind.PIPE, "R1", "J1",
                        bd("1000"), 300, "PVC_PBA", null, null, null, null, true),
                new WaterArc("B1", "Bomba", WaterArc.WaterArcKind.PUMP, "J1", "T1",
                        null, null, null, null, bd("15"), null, null, true),
                new WaterArc("P2", "Distribuicao", WaterArc.WaterArcKind.PIPE, "T1", "J2",
                        bd("800"), 200, "DUCTILE_IRON", null, null, null, null, true),
                new WaterArc("V1", "PRV", WaterArc.WaterArcKind.VALVE, "J1", "J2",
                        null, 200, null, null, null, "PRV", bd("30"), true));
        return new WaterSnapshot(request(), "Rede de exemplo", nodes, arcs,
                Map.of("J1", bd("51840"), "J2", bd("25920")), 30,
                Map.of("PVC_PBA", bd("140"), "DUCTILE_IRON", bd("130")), List.of());
    }

    /** Rede de esgoto/drenagem mínima executável no SWMM: PV → conduto → outfall, sub-bacia e boca de lobo. */
    static SewerSnapshot sampleSewer() {
        var nodes = List.of(
                new SewerNode("PV1", "PV 1", SewerNode.SewerNodeKind.JUNCTION, bd("10"), bd("12.5"), null, null, bd("2")),
                new SewerNode("PV2", "PV 2", SewerNode.SewerNodeKind.JUNCTION, bd("9"), bd("11.5"), null, null, null),
                new SewerNode("DET1", "Detencao", SewerNode.SewerNodeKind.STORAGE, bd("8.5"), null, bd("3"), bd("400"), null),
                new SewerNode("OUT1", "Lancamento", SewerNode.SewerNodeKind.OUTFALL_FREE, bd("8"), bd("11"), null, null, null));
        var arcs = List.of(
                new SewerArc("C1", "Coletor", "PV1", "PV2", bd("100"), "CONCRETE", null,
                        "CIRCULAR", bd("0.6"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0")),
                new SewerArc("C2", "Emissario", "PV2", "OUT1", bd("80"), "CONCRETE", null,
                        "CIRCULAR", bd("0.8"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0")),
                new SewerArc("C3", "Desvio detencao", "PV2", "DET1", bd("30"), "PVC_SOLID_WALL", null,
                        "CIRCULAR", bd("0.4"), bd("0"), bd("0"), bd("0"), bd("0"), bd("0")));
        var subs = List.of(new Subcatchment("SB1", "RG1", "PV1", bd("2.5"), bd("60"), bd("120"), bd("1.5"),
                bd("0.012"), bd("0.15"), bd("1.27"), bd("2.54"), bd("25"), "HORTON",
                Map.of("max_rate", bd("75"), "min_rate", bd("5"), "decay", bd("4"))));
        var inlets = List.of(new Inlet("BL1", "GRATE", bd("0.9"), bd("0.6"), null, "GENERIC", bd("0.5"), "C1", "PV1", 1, bd("0")));
        return new SewerSnapshot(request(), "Rede de esgoto de exemplo", nodes, arcs, subs, inlets,
                Map.of("CONCRETE", bd("0.013"), "PVC_SOLID_WALL", bd("0.010")));
    }
}
