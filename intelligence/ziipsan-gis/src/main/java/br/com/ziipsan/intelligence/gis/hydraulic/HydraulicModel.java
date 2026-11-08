package br.com.ziipsan.intelligence.gis.hydraulic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GIS-12 (ED-63) — modelo de entrada da hidráulica, lido da topologia
 * persistida (gis_node/gis_arc/gis_structure/gis_zone da GIS-01), NUNCA da
 * view de compatibilidade gis_network_element (removida nesta tarefa, M5/M6
 * do GIS-CADASTRO-TECNICO §2.3). Registros desligados de JPA/JTS para o
 * núcleo ser testável sem banco; quem monta é o EpanetSimulationService /
 * GisHydraulicSnapshotLoader sobre as tabelas novas.
 *
 * Nenhum número nasce aqui: rugosidade vem de gis_material
 * (hazen_williams_c / manning_n do catálogo de fábrica da P1.27 ou do
 * tenant), demanda vem do consumo faturado das ligações do nó (GIS-05,
 * P2.23), cotas e dimensões vêm das colunas tipadas das feições.
 */
public final class HydraulicModel {

    private HydraulicModel() {
    }

    /** Pedido de recorte: zona, estados do ciclo de vida e data (M5: "por zona, estado e data"). */
    public record SnapshotRequest(String zoneCode, Set<String> lifecycleStatuses, LocalDate asOf) {
        public SnapshotRequest {
            if (zoneCode == null || zoneCode.isBlank()) throw new IllegalArgumentException("zona obrigatoria");
            if (lifecycleStatuses == null || lifecycleStatuses.isEmpty()) lifecycleStatuses = Set.of("IN_SERVICE");
            if (asOf == null) asOf = LocalDate.now();
        }
    }

    /** Nó de água: junção, reservatório de carga fixa, tanque ou nó de ligação (válvula/bomba). */
    public record WaterNode(String code, String name, WaterNodeKind kind,
                            BigDecimal elevationM, BigDecimal headM,
                            BigDecimal initLevelM, BigDecimal minLevelM, BigDecimal maxLevelM,
                            BigDecimal diameterM, BigDecimal x, BigDecimal y) {
        public enum WaterNodeKind {JUNCTION, RESERVOIR, TANK}
    }

    /** Arco de água: tubulação, bomba ou válvula, com pontas da topologia persistida (node_1/node_2). */
    public record WaterArc(String code, String name, WaterArcKind kind,
                           String node1Code, String node2Code,
                           BigDecimal lengthM, Integer diameterMm, String materialCode,
                           BigDecimal roughnessOverride, BigDecimal powerKw,
                           String valveType, BigDecimal setting, boolean open) {
        public enum WaterArcKind {PIPE, PUMP, VALVE}
    }

    /** Nó de esgoto/drenagem para o SWMM: PV/junção, lançamento (outfall) ou armazenamento. */
    public record SewerNode(String code, String name, SewerNodeKind kind,
                            BigDecimal invertM, BigDecimal rimM,
                            BigDecimal storageMaxDepthM, BigDecimal storageConstantAreaM2,
                            BigDecimal dryWeatherFlowLps) {
        public enum SewerNodeKind {JUNCTION, OUTFALL_FREE, OUTFALL_FIXED, STORAGE}

        /** Profundidade máxima do SWMM = tampa − fundo (§11 do GIS-CADASTRO-TECNICO). */
        public BigDecimal maxDepthM() {
            if (kind == SewerNodeKind.STORAGE) return storageMaxDepthM;
            if (invertM == null || rimM == null) return null;
            return rimM.subtract(invertM);
        }
    }

    /** Arco de esgoto/drenagem: conduto com seção geom1..geom4 do item de catálogo e Manning do material. */
    public record SewerArc(String code, String name, String node1Code, String node2Code,
                           BigDecimal lengthM, String materialCode, BigDecimal manningOverride,
                           String sectionShape, BigDecimal geom1, BigDecimal geom2,
                           BigDecimal geom3, BigDecimal geom4,
                           BigDecimal inOffsetM, BigDecimal outOffsetM) {
    }

    /** Sub-bacia (ED-64, §4.4): atributos do SWMM guardados na zona STORM_SUBCATCHMENT/SEWER_SUBBASIN. */
    public record Subcatchment(String code, String rainGageCode, String outletCode,
                               BigDecimal areaHa, BigDecimal imperviousPct, BigDecimal widthM,
                               BigDecimal slopePct, BigDecimal nImperv, BigDecimal nPerv,
                               BigDecimal sImpervMm, BigDecimal sPervMm, BigDecimal pctZeroImperv,
                               String infiltrationModel, Map<String, BigDecimal> infiltrationParams) {
    }

    /** Boca de lobo (ED-64: INLET) com desenho e uso no conduto (SWMM 5.2 [INLETS]/[INLET_USAGE]). */
    public record Inlet(String designCode, String inletKind, BigDecimal lengthM, BigDecimal widthM,
                        BigDecimal heightM, String grateType, BigDecimal grateOpenAreaRatio,
                        String conduitCode, String receivingNodeCode, int count, BigDecimal clogPct) {
    }

    /**
     * Foto da rede de água num recorte. {@code billedVolumeM3ByNode} é o
     * consumo faturado agregado das ligações ligadas a cada nó no período
     * (GIS-05); {@code materialHwC} é o C de Hazen-Williams por código de
     * material lido de gis_material. {@code controls} são linhas [CONTROLS]
     * já formadas pelos cenários (ex.: LINK x CLOSED AT TIME 0).
     */
    public record WaterSnapshot(SnapshotRequest request, String title,
                                List<WaterNode> nodes, List<WaterArc> arcs,
                                Map<String, BigDecimal> billedVolumeM3ByNode,
                                int billedPeriodDays,
                                Map<String, BigDecimal> materialHwC,
                                List<String> controls) {
        public WaterSnapshot {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            arcs = arcs == null ? List.of() : List.copyOf(arcs);
            billedVolumeM3ByNode = billedVolumeM3ByNode == null ? Map.of() : Map.copyOf(billedVolumeM3ByNode);
            materialHwC = materialHwC == null ? Map.of() : Map.copyOf(materialHwC);
            controls = controls == null ? List.of() : List.copyOf(controls);
        }

        /**
         * Demanda do nó em L/s a partir do consumo faturado: volume do
         * período (m³) ÷ dias ÷ 86400 × 1000. Nó sem ligação faturada tem
         * demanda zero declarada — nunca estimada.
         */
        public BigDecimal demandLps(String nodeCode) {
            BigDecimal v = billedVolumeM3ByNode.get(nodeCode);
            if (v == null || billedPeriodDays <= 0) return BigDecimal.ZERO;
            return v.multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf((long) billedPeriodDays * 86400L), 6, java.math.RoundingMode.HALF_UP);
        }
    }

    /** Foto da rede de esgoto/drenagem para o SWMM 5.2. */
    public record SewerSnapshot(SnapshotRequest request, String title,
                                List<SewerNode> nodes, List<SewerArc> arcs,
                                List<Subcatchment> subcatchments, List<Inlet> inlets,
                                Map<String, BigDecimal> materialManning) {
        public SewerSnapshot {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            arcs = arcs == null ? List.of() : List.copyOf(arcs);
            subcatchments = subcatchments == null ? List.of() : List.copyOf(subcatchments);
            inlets = inlets == null ? List.of() : List.copyOf(inlets);
            materialManning = materialManning == null ? Map.of() : Map.copyOf(materialManning);
        }
    }
}
