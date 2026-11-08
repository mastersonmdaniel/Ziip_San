package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.Inlet;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SewerArc;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SewerNode;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SewerSnapshot;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.Subcatchment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GIS-12: escreve o .inp do SWMM 5.2 de esgoto e drenagem (§11 do
 * GIS-CADASTRO-TECNICO): junção = PV (fundo; profundidade máxima =
 * tampa − fundo), conduto = arco com seção geom1–geom4 e Manning do
 * material, armazenamento = bacia de detenção (FUNCTIONAL com área
 * constante declarada), outfall = lançamento, subcatchment = sub-bacia
 * da GIS-13 com o modelo de infiltração dela, inlet = boca de lobo com
 * [INLETS] + [INLET_USAGE] no formato do inlet.c do SWMM 5.2.
 * Unidades: FLOW UNITS LPS, INFILTRATION conforme a sub-bacia.
 * [SUBAREAS]: quando a sub-bacia da GIS-13 não declara N/depressão, o
 * escritor usa os padrões do próprio manual do SWMM 5.2 (N-Imperv 0,01;
 * N-Perv 0,1; S-Imperv 1,27 mm; S-Perv 2,54 mm; %Zero 25) — padrão da
 * ferramenta, declarado aqui, não dado de cadastro.
 */
public final class SwmmInpWriter {

    private SwmmInpWriter() {
    }

    public static BigDecimal manning(SewerSnapshot s, SewerArc a) {
        if (a.manningOverride() != null) return a.manningOverride();
        return a.materialCode() == null ? null : s.materialManning().get(a.materialCode());
    }

    public static List<String> validate(SewerSnapshot s) {
        List<String> issues = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        for (SewerNode n : s.nodes()) {
            if (!InpText.validId(n.code())) issues.add("No " + n.code() + ": ID invalido para o SWMM.");
            if (!nodeIds.add(n.code())) issues.add("No " + n.code() + ": ID repetido.");
            if (n.invertM() == null) issues.add("No " + n.code() + " sem cota de fundo (invert_elevation_m).");
            if (n.maxDepthM() == null) issues.add("No " + n.code() + " sem profundidade maxima (tampa - fundo ou profundidade do armazenamento).");
            if (n.kind() == SewerNode.SewerNodeKind.STORAGE && n.storageConstantAreaM2() == null) {
                issues.add("Armazenamento " + n.code() + " sem area constante declarada (FUNCTIONAL).");
            }
        }
        Set<String> linkIds = new HashSet<>();
        for (SewerArc a : s.arcs()) {
            if (!InpText.validId(a.code())) issues.add("Conduto " + a.code() + ": ID invalido para o SWMM.");
            if (!linkIds.add(a.code())) issues.add("Conduto " + a.code() + ": ID repetido.");
            for (String end : new String[]{a.node1Code(), a.node2Code()}) {
                if (end == null || !nodeIds.contains(end)) issues.add("Conduto " + a.code() + " aponta para o no inexistente " + end + ".");
            }
            if (a.lengthM() == null || a.lengthM().signum() <= 0) issues.add("Conduto " + a.code() + " sem comprimento positivo.");
            if (manning(s, a) == null) issues.add("Conduto " + a.code() + " sem Manning: material " + a.materialCode()
                    + " sem manning_n em gis_material e sem sobrescrita do elemento.");
            if (a.sectionShape() == null || a.geom1() == null) issues.add("Conduto " + a.code() + " sem secao (geom1..geom4 do item de catalogo).");
        }
        for (Subcatchment sc : s.subcatchments()) {
            if (sc.outletCode() == null || !nodeIds.contains(sc.outletCode())) {
                issues.add("Sub-bacia " + sc.code() + " sem saida valida (outlet_ref): " + sc.outletCode() + ".");
            }
            if (sc.areaHa() == null || sc.imperviousPct() == null || sc.widthM() == null || sc.slopePct() == null
                    || sc.infiltrationModel() == null) {
                issues.add("Sub-bacia " + sc.code() + " com atributos do SWMM incompletos (area/imperv/largura/declividade/infiltracao).");
            }
        }
        for (Inlet in : s.inlets()) {
            if (("GRATE".equalsIgnoreCase(in.inletKind() == null ? "" : in.inletKind())
                    || "DROP_GRATE".equalsIgnoreCase(in.inletKind() == null ? "" : in.inletKind()))
                    && "GENERIC".equalsIgnoreCase(in.grateType() == null ? "GENERIC" : in.grateType())
                    && in.grateOpenAreaRatio() == null) {
                issues.add("Boca de lobo " + in.designCode() + ": grelha GENERIC sem fracao de area aberta (exigida pelo SWMM 5.2).");
            }
            if (!linkIds.contains(in.conduitCode())) issues.add("Boca de lobo " + in.designCode() + " ligada ao conduto inexistente " + in.conduitCode() + ".");
            if (!nodeIds.contains(in.receivingNodeCode())) issues.add("Boca de lobo " + in.designCode() + " sem no receptor valido.");
        }
        if (s.nodes().stream().noneMatch(n -> n.kind() != SewerNode.SewerNodeKind.JUNCTION)) {
            issues.add("A rede nao tem outfall nem armazenamento: o SWMM nao tem para onde escoar.");
        }
        return issues;
    }

    public static String write(SewerSnapshot s) {
        List<String> issues = validate(s);
        if (!issues.isEmpty()) throw new HydraulicValidationException(issues);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("[TITLE]\n").append(InpText.oneLine(s.title())).append('\n');
        sb.append(";ZiipSan GIS-12 zona=").append(s.request().zoneCode())
                .append(" estados=").append(s.request().lifecycleStatuses())
                .append(" data=").append(s.request().asOf()).append('\n');

        sb.append("\n[OPTIONS]\nFLOW_UNITS\tLPS\nINFILTRATION\tHORTON\nFLOW_ROUTING\tDYNWAVE\n")
                .append("START_DATE\t01/01/2026\nSTART_TIME\t00:00:00\nEND_DATE\t01/01/2026\nEND_TIME\t06:00:00\n")
                .append("REPORT_STEP\t00:15:00\nWET_STEP\t00:05:00\nDRY_STEP\t01:00:00\nROUTING_STEP\t00:00:30\n");

        sb.append("\n[JUNCTIONS]\n;ID\tInvertElev\tMaxDepth\tInitDepth\tSurchargeDepth\tPondedArea\n");
        for (SewerNode n : s.nodes()) if (n.kind() == SewerNode.SewerNodeKind.JUNCTION) {
            sb.append(n.code()).append('\t').append(InpText.num(n.invertM())).append('\t')
                    .append(InpText.num(n.maxDepthM())).append("\t0\t0\t0\n");
        }
        sb.append("\n[OUTFALLS]\n;ID\tInvertElev\tType\tStageData\n");
        for (SewerNode n : s.nodes()) if (n.kind() == SewerNode.SewerNodeKind.OUTFALL_FREE
                || n.kind() == SewerNode.SewerNodeKind.OUTFALL_FIXED) {
            sb.append(n.code()).append('\t').append(InpText.num(n.invertM())).append('\t')
                    .append(n.kind() == SewerNode.SewerNodeKind.OUTFALL_FREE ? "FREE" : "FIXED")
                    .append('\t').append(n.kind() == SewerNode.SewerNodeKind.OUTFALL_FIXED ? InpText.num(n.rimM()) : "").append('\n');
        }
        sb.append("\n[STORAGE]\n;ID\tInvertElev\tMaxDepth\tInitDepth\tShape\tShapeParams\tEvapFrac\n");
        for (SewerNode n : s.nodes()) if (n.kind() == SewerNode.SewerNodeKind.STORAGE) {
            sb.append(n.code()).append('\t').append(InpText.num(n.invertM())).append('\t')
                    .append(InpText.num(n.maxDepthM())).append("\t0\tFUNCTIONAL\t0\t0\t")
                    .append(InpText.num(n.storageConstantAreaM2())).append("\t0\n");
        }
        boolean anyDwf = s.nodes().stream().anyMatch(n -> n.dryWeatherFlowLps() != null && n.dryWeatherFlowLps().signum() > 0);
        if (anyDwf) {
            sb.append("\n[DWF]\n;Node\tParameter\tAverageValue\n");
            for (SewerNode n : s.nodes()) if (n.dryWeatherFlowLps() != null && n.dryWeatherFlowLps().signum() > 0) {
                sb.append(n.code()).append("\tFLOW\t").append(InpText.num(n.dryWeatherFlowLps(), 6)).append('\n');
            }
        }
        sb.append("\n[CONDUITS]\n;ID\tInletNode\tOutletNode\tLength\tRoughness\tInOffset\tOutOffset\tInitFlow\tMaxFlow\n");
        for (SewerArc a : s.arcs()) {
            sb.append(a.code()).append('\t').append(a.node1Code()).append('\t').append(a.node2Code()).append('\t')
                    .append(InpText.num(a.lengthM(), 3)).append('\t').append(InpText.num(manning(s, a), 4)).append('\t')
                    .append(InpText.num(a.inOffsetM() == null ? BigDecimal.ZERO : a.inOffsetM())).append('\t')
                    .append(InpText.num(a.outOffsetM() == null ? BigDecimal.ZERO : a.outOffsetM())).append("\t0\t0\n");
        }
        sb.append("\n[XSECTIONS]\n;Link\tShape\tGeom1\tGeom2\tGeom3\tGeom4\tBarrels\n");
        for (SewerArc a : s.arcs()) {
            sb.append(a.code()).append('\t').append(a.sectionShape().trim().toUpperCase(java.util.Locale.ROOT)).append('\t')
                    .append(InpText.num(a.geom1())).append('\t').append(InpText.num(a.geom2() == null ? BigDecimal.ZERO : a.geom2()))
                    .append('\t').append(InpText.num(a.geom3() == null ? BigDecimal.ZERO : a.geom3()))
                    .append('\t').append(InpText.num(a.geom4() == null ? BigDecimal.ZERO : a.geom4())).append("\t1\n");
        }
        if (!s.subcatchments().isEmpty()) {
            sb.append("\n[SUBCATCHMENTS]\n;ID\tRainGage\tOutlet\tArea\t%Imperv\tWidth\t%Slope\tCurbLen\n");
            for (Subcatchment sc : s.subcatchments()) {
                sb.append(sc.code()).append('\t').append(sc.rainGageCode() == null ? "RG1" : sc.rainGageCode()).append('\t')
                        .append(sc.outletCode()).append('\t').append(InpText.num(sc.areaHa())).append('\t')
                        .append(InpText.num(sc.imperviousPct())).append('\t').append(InpText.num(sc.widthM())).append('\t')
                        .append(InpText.num(sc.slopePct())).append("\t0\n");
            }
            sb.append("\n[SUBAREAS]\n;Subcatchment\tN-Imperv\tN-Perv\tS-Imperv\tS-Perv\tPctZero\tRouteTo\n");
            for (Subcatchment sc : s.subcatchments()) {
                sb.append(sc.code()).append('\t')
                        .append(InpText.num(sc.nImperv() == null ? new BigDecimal("0.01") : sc.nImperv())).append('\t')
                        .append(InpText.num(sc.nPerv() == null ? new BigDecimal("0.1") : sc.nPerv())).append('\t')
                        .append(InpText.num(sc.sImpervMm() == null ? new BigDecimal("1.27") : sc.sImpervMm())).append('\t')
                        .append(InpText.num(sc.sPervMm() == null ? new BigDecimal("2.54") : sc.sPervMm())).append('\t')
                        .append(InpText.num(sc.pctZeroImperv() == null ? new BigDecimal("25") : sc.pctZeroImperv()))
                        .append("\tOUTLET\n");
            }
            sb.append("\n[INFILTRATION]\n;Subcatchment\tParameters\n");
            for (Subcatchment sc : s.subcatchments()) {
                sb.append(sc.code()).append('\t').append(infiltrationLine(sc)).append('\n');
            }
            sb.append("\n[RAINGAGES]\n;ID\tFormat\tInterval\tSCF\tSource\nRG1\tINTENSITY\t0:15\t1.0\tTIMESERIES TS1\n");
            sb.append("\n[TIMESERIES]\n;ID\tDate\tTime\tValue\nTS1\t\t0:00\t0\n");
        }
        if (!s.inlets().isEmpty()) {
            sb.append("\n[INLETS]\n;ID\tType\tParameters\n");
            for (Inlet in : s.inlets()) {
                sb.append(in.designCode()).append('\t').append(inletDesignLine(in)).append('\n');
            }
            sb.append("\n[INLET_USAGE]\n;Conduit\tInletID\tNodeID\tNumInlets\t%Clogged\tQmax\n");
            for (Inlet in : s.inlets()) {
                sb.append(in.conduitCode()).append('\t').append(in.designCode()).append('\t').append(in.receivingNodeCode())
                        .append('\t').append(Math.max(1, in.count())).append('\t')
                        .append(InpText.num(in.clogPct() == null ? BigDecimal.ZERO : in.clogPct())).append("\t0\n");
            }
        }
        sb.append("\n[REPORT]\nINPUT\tNO\nCONTROLS\tNO\nSUBCATCHMENTS\tALL\nNODES\tALL\nLINKS\tALL\n");
        // SWMM 5.2 nao tem secao [END] (ERROR 205): o arquivo termina no [REPORT].
        return sb.toString();
    }

    private static String infiltrationLine(Subcatchment sc) {
        var p = sc.infiltrationParams() == null ? java.util.Map.<String, BigDecimal>of() : sc.infiltrationParams();
        return switch (sc.infiltrationModel().trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HORTON" -> InpText.num(p.get("max_rate")) + "\t" + InpText.num(p.get("min_rate")) + "\t"
                    + InpText.num(p.get("decay")) + "\t" + InpText.num(p.getOrDefault("dry_days", BigDecimal.ZERO)) + "\t0";
            case "GREEN_AMPT" -> InpText.num(p.get("suction")) + "\t" + InpText.num(p.get("conductivity")) + "\t" + InpText.num(p.get("deficit"));
            case "CURVE_NUMBER" -> InpText.num(p.get("curve_number")) + "\t" + InpText.num(p.getOrDefault("dry_days", BigDecimal.ZERO));
            default -> throw new HydraulicValidationException(List.of(
                    "Sub-bacia " + sc.code() + ": modelo de infiltracao fora da lista do SWMM 5.2: " + sc.infiltrationModel()));
        };
    }

    /** Linha de [INLETS] no formato do inlet.c: GRATE L W Tipo / CURB L H / SLOTTED L W / DROP_*. */
    private static String inletDesignLine(Inlet in) {
        String kind = in.inletKind() == null ? "" : in.inletKind().trim().toUpperCase(java.util.Locale.ROOT);
        return switch (kind) {
            case "GRATE", "DROP_GRATE" -> {
                String type = in.grateType() == null ? "GENERIC" : in.grateType();
                // inlet.c do SWMM 5.2: grelha GENERIC exige a fracao de area aberta (6o token).
                yield kind + "\t" + InpText.num(in.lengthM()) + "\t" + InpText.num(in.widthM()) + "\t" + type
                        + ("GENERIC".equalsIgnoreCase(type) ? "\t" + InpText.num(in.grateOpenAreaRatio()) : "");
            }
            case "CURB", "DROP_CURB" -> kind + "\t" + InpText.num(in.lengthM()) + "\t" + InpText.num(in.heightM());
            case "SLOTTED" -> kind + "\t" + InpText.num(in.lengthM()) + "\t" + InpText.num(in.widthM());
            default -> throw new HydraulicValidationException(List.of(
                    "Boca de lobo " + in.designCode() + ": inlet_kind fora dos tipos do SWMM 5.2: " + in.inletKind()));
        };
    }
}
