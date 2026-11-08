package br.com.ziipsan.intelligence.gis.hydraulic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GIS-12 / SG-1339 (parte INP): importação de .inp do EPANET e do SWMM
 * COMO PACOTE — o resultado é um conjunto de feições com o código
 * preservado, pronto para entrar no pacote de edição da GIS-04 (revisão
 * por outra pessoa), nunca escrita direta na base. O SRS é declarado no
 * pedido (obrigatório: .inp não carrega SRS) e o ida-e-volta preserva o
 * código das feições (teste de ida e volta sobre os escritores).
 */
public final class InpImporter {

    private InpImporter() {
    }

    public enum ModelKind {EPANET, SWMM}

    /** Feição importada: código original, papel no modelo e campos brutos da linha. */
    public record ImportedFeature(String code, String role, Map<String, String> fields) {
        public ImportedFeature {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }
    }

    /** Pacote de importação: feições + SRS declarado + problemas (linha a linha, sem descartar em silêncio). */
    public record ImportedPackage(ModelKind kind, String declaredSrs, List<ImportedFeature> features,
                                  List<String> issues) {
        public ImportedPackage {
            features = features == null ? List.of() : List.copyOf(features);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public List<String> codes() {
            return features.stream().map(ImportedFeature::code).toList();
        }
    }

    /** Seções do .inp: nome em maiúsculas → linhas de tokens (comentários ';' removidos). */
    public static Map<String, List<String[]>> parseSections(String inp) {
        Map<String, List<String[]>> sections = new LinkedHashMap<>();
        String current = null;
        for (String raw : inp.split("\\R")) {
            String line = raw;
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length() - 1).trim().toUpperCase(Locale.ROOT);
                sections.putIfAbsent(current, new ArrayList<>());
                continue;
            }
            if (current == null) continue;
            sections.computeIfAbsent(current, k -> new ArrayList<>()).add(line.split("\\s+"));
        }
        return sections;
    }

    public static ImportedPackage importEpanet(String inp, String declaredSrs) {
        List<String> issues = new ArrayList<>();
        if (declaredSrs == null || declaredSrs.isBlank()) issues.add("SRS nao declarado: a importacao INP exige o SRS do desenho (SG-1339).");
        var sec = parseSections(inp);
        List<ImportedFeature> out = new ArrayList<>();
        addAll(out, sec.get("JUNCTIONS"), "JUNCTION", new String[]{"elevation", "demand"});
        addAll(out, sec.get("RESERVOIRS"), "RESERVOIR", new String[]{"head"});
        addAll(out, sec.get("TANKS"), "TANK", new String[]{"elevation", "initLevel", "minLevel", "maxLevel", "diameter"});
        addAll(out, sec.get("PIPES"), "PIPE", new String[]{"node1", "node2", "length", "diameter", "roughness"});
        addAll(out, sec.get("PUMPS"), "PUMP", new String[]{"node1", "node2", "parameters"});
        addAll(out, sec.get("VALVES"), "VALVE", new String[]{"node1", "node2", "diameter", "type", "setting"});
        if (out.isEmpty()) issues.add("Nenhuma feicao reconhecida no .inp EPANET.");
        return new ImportedPackage(ModelKind.EPANET, declaredSrs, out, issues);
    }

    public static ImportedPackage importSwmm(String inp, String declaredSrs) {
        List<String> issues = new ArrayList<>();
        if (declaredSrs == null || declaredSrs.isBlank()) issues.add("SRS nao declarado: a importacao INP exige o SRS do desenho (SG-1339).");
        var sec = parseSections(inp);
        List<ImportedFeature> out = new ArrayList<>();
        addAll(out, sec.get("JUNCTIONS"), "JUNCTION", new String[]{"invert", "maxDepth"});
        addAll(out, sec.get("OUTFALLS"), "OUTFALL", new String[]{"invert", "type"});
        addAll(out, sec.get("STORAGE"), "STORAGE", new String[]{"invert", "maxDepth"});
        addAll(out, sec.get("CONDUITS"), "CONDUIT", new String[]{"inletNode", "outletNode", "length", "roughness"});
        addAll(out, sec.get("SUBCATCHMENTS"), "SUBCATCHMENT", new String[]{"rainGage", "outlet", "area", "imperv", "width", "slope"});
        addAll(out, sec.get("INLETS"), "INLET", new String[]{"type"});
        if (out.isEmpty()) issues.add("Nenhuma feicao reconhecida no .inp SWMM.");
        return new ImportedPackage(ModelKind.SWMM, declaredSrs, out, issues);
    }

    private static void addAll(List<ImportedFeature> out, List<String[]> rows, String role, String[] fieldNames) {
        if (rows == null) return;
        for (String[] tok : rows) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 0; i < fieldNames.length && i + 1 < tok.length; i++) fields.put(fieldNames[i], tok[i + 1]);
            out.add(new ImportedFeature(tok[0], role, fields));
        }
    }
}
