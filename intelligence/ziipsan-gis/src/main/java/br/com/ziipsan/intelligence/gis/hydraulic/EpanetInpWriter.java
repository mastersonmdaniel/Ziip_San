package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterArc;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterNode;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GIS-12: escreve o .inp do EPANET 2.2 a partir da foto da topologia
 * persistida, por zona, estado e data (o recorte já vem aplicado na foto).
 * Rugosidade = C de Hazen-Williams do material (gis_material), com
 * sobrescrita declarada do elemento quando existir; material sem C é
 * problema registrado, nunca padrão silencioso. Demanda = consumo faturado
 * das ligações do nó (ver {@link WaterSnapshot#demandLps}). Unidades LPS,
 * perda H-W — mesma convenção do InpWriter da GIS-03. O escritor não
 * corrige nem completa o modelo: problema vira exceção com a lista toda.
 */
public final class EpanetInpWriter {

    private EpanetInpWriter() {
    }

    public static List<String> validate(WaterSnapshot s) {
        List<String> issues = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        for (WaterNode n : s.nodes()) {
            if (!InpText.validId(n.code())) issues.add("No " + n.code() + ": ID invalido para o EPANET.");
            if (!nodeIds.add(n.code())) issues.add("No " + n.code() + ": ID repetido.");
            switch (n.kind()) {
                case JUNCTION -> {
                    if (n.elevationM() == null) issues.add("No " + n.code() + " sem cota (ground_elevation_m).");
                }
                case RESERVOIR -> {
                    if (n.headM() == null) issues.add("Reservatorio " + n.code() + " sem carga (head_m).");
                }
                case TANK -> {
                    if (n.elevationM() == null || n.initLevelM() == null || n.minLevelM() == null
                            || n.maxLevelM() == null || n.diameterM() == null) {
                        issues.add("Tanque " + n.code() + " com dados incompletos (cota/niveis/diametro).");
                    }
                }
            }
        }
        Set<String> linkIds = new HashSet<>();
        for (WaterArc a : s.arcs()) {
            if (!InpText.validId(a.code())) issues.add("Arco " + a.code() + ": ID invalido para o EPANET.");
            if (!linkIds.add(a.code())) issues.add("Arco " + a.code() + ": ID repetido.");
            for (String end : new String[]{a.node1Code(), a.node2Code()}) {
                if (end == null || !nodeIds.contains(end)) {
                    issues.add("Arco " + a.code() + " aponta para o no inexistente " + end + ".");
                }
            }
            switch (a.kind()) {
                case PIPE -> {
                    if (a.lengthM() == null || a.lengthM().signum() <= 0) issues.add("Tubulacao " + a.code() + " sem comprimento positivo.");
                    if (a.diameterMm() == null || a.diameterMm() <= 0) issues.add("Tubulacao " + a.code() + " sem diametro (dn do item de catalogo).");
                    if (roughness(s, a) == null) issues.add("Tubulacao " + a.code() + " sem rugosidade: material "
                            + a.materialCode() + " sem hazen_williams_c em gis_material e sem sobrescrita do elemento.");
                }
                case PUMP -> {
                    if (a.powerKw() == null || a.powerKw().signum() <= 0) issues.add("Bomba " + a.code() + " sem potencia positiva (power_kw).");
                }
                case VALVE -> {
                    if (a.valveType() == null || a.valveType().isBlank()) issues.add("Valvula " + a.code() + " sem tipo (PRV/PSV/PBV/FCV/TCV).");
                    if (a.setting() == null) issues.add("Valvula " + a.code() + " sem regulagem (setting).");
                    if (a.diameterMm() == null || a.diameterMm() <= 0) issues.add("Valvula " + a.code() + " sem diametro.");
                }
            }
        }
        boolean hasSource = s.nodes().stream().anyMatch(n -> n.kind() != WaterNode.WaterNodeKind.JUNCTION);
        if (!hasSource) issues.add("A rede nao tem reservatorio nem tanque: sem fonte de carga o EPANET nao resolve.");
        if (s.arcs().isEmpty()) issues.add("A rede nao tem nenhuma ligacao.");
        return issues;
    }

    /** C efetivo: sobrescrita do elemento, senão o C do material em gis_material; null se nenhum. */
    public static BigDecimal roughness(WaterSnapshot s, WaterArc a) {
        if (a.roughnessOverride() != null) return a.roughnessOverride();
        return a.materialCode() == null ? null : s.materialHwC().get(a.materialCode());
    }

    public static String write(WaterSnapshot s) {
        List<String> issues = validate(s);
        if (!issues.isEmpty()) throw new HydraulicValidationException(issues);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("[TITLE]\n").append(InpText.oneLine(s.title())).append('\n');
        sb.append(";ZiipSan GIS-12 zona=").append(s.request().zoneCode())
                .append(" estados=").append(s.request().lifecycleStatuses())
                .append(" data=").append(s.request().asOf()).append('\n');

        sb.append("\n[JUNCTIONS]\n;ID\tElev\tDemand\n");
        for (WaterNode n : s.nodes()) if (n.kind() == WaterNode.WaterNodeKind.JUNCTION) {
            sb.append(n.code()).append('\t').append(InpText.num(n.elevationM())).append('\t')
                    .append(InpText.num(s.demandLps(n.code()), 6)).append('\n');
        }
        sb.append("\n[RESERVOIRS]\n;ID\tHead\n");
        for (WaterNode n : s.nodes()) if (n.kind() == WaterNode.WaterNodeKind.RESERVOIR) {
            sb.append(n.code()).append('\t').append(InpText.num(n.headM())).append('\n');
        }
        sb.append("\n[TANKS]\n;ID\tElevation\tInitLevel\tMinLevel\tMaxLevel\tDiameter\tMinVol\n");
        for (WaterNode n : s.nodes()) if (n.kind() == WaterNode.WaterNodeKind.TANK) {
            sb.append(n.code()).append('\t').append(InpText.num(n.elevationM())).append('\t')
                    .append(InpText.num(n.initLevelM())).append('\t').append(InpText.num(n.minLevelM())).append('\t')
                    .append(InpText.num(n.maxLevelM())).append('\t').append(InpText.num(n.diameterM())).append("\t0\n");
        }
        sb.append("\n[PIPES]\n;ID\tNode1\tNode2\tLength\tDiameter\tRoughness\tMinorLoss\tStatus\n");
        for (WaterArc a : s.arcs()) if (a.kind() == WaterArc.WaterArcKind.PIPE) {
            sb.append(a.code()).append('\t').append(a.node1Code()).append('\t').append(a.node2Code()).append('\t')
                    .append(InpText.num(a.lengthM(), 3)).append('\t').append(a.diameterMm()).append('\t')
                    .append(InpText.num(roughness(s, a), 1)).append("\t0\t").append(a.open() ? "Open" : "Closed").append('\n');
        }
        sb.append("\n[PUMPS]\n;ID\tNode1\tNode2\tParameters\n");
        for (WaterArc a : s.arcs()) if (a.kind() == WaterArc.WaterArcKind.PUMP) {
            sb.append(a.code()).append('\t').append(a.node1Code()).append('\t').append(a.node2Code())
                    .append("\tPOWER ").append(InpText.num(a.powerKw())).append('\n');
        }
        sb.append("\n[VALVES]\n;ID\tNode1\tNode2\tDiameter\tType\tSetting\tMinorLoss\n");
        for (WaterArc a : s.arcs()) if (a.kind() == WaterArc.WaterArcKind.VALVE) {
            sb.append(a.code()).append('\t').append(a.node1Code()).append('\t').append(a.node2Code()).append('\t')
                    .append(a.diameterMm()).append('\t').append(a.valveType().trim().toUpperCase(java.util.Locale.ROOT))
                    .append('\t').append(InpText.num(a.setting())).append("\t0\n");
        }
        if (!s.controls().isEmpty()) {
            sb.append("\n[CONTROLS]\n");
            for (String c : s.controls()) sb.append(InpText.oneLine(c)).append('\n');
        }
        sb.append("\n[OPTIONS]\nUnits\t\t\tLPS\nHeadloss\t\tH-W\nSpecific Gravity\t1.0\nViscosity\t\t1.0\n")
                .append("Trials\t\t\t40\nAccuracy\t\t0.001\nUnbalanced\t\tContinue 10\nDemand Multiplier\t1.0\n")
                .append("Emitter Exponent\t0.5\nQuality\t\t\tNone\nDemand Model\t\tDDA\n");
        sb.append("\n[TIMES]\nDuration\t\t0:00:00\nHydraulic Timestep\t1:00:00\nQuality Timestep\t1:00:00\n")
                .append("Pattern Timestep\t1:00:00\nPattern Start\t\t0:00:00\nReport Timestep\t\t1:00:00\n")
                .append("Report Start\t\t0:00:00\nStart ClockTime\t\t12 AM\nStatistic\t\tNONE\n");
        sb.append("\n[REPORT]\nStatus\t\tYes\nSummary\t\tNo\nNodes\t\tAll\nLinks\t\tAll\n");
        sb.append("\n[COORDINATES]\n;Node\tX-Coord\tY-Coord\n");
        for (WaterNode n : s.nodes()) if (n.x() != null && n.y() != null) {
            sb.append(n.code()).append('\t').append(InpText.num(n.x(), 8)).append('\t').append(InpText.num(n.y(), 8)).append('\n');
        }
        sb.append("\n[END]\n");
        return sb.toString();
    }
}
