package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterNode;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterSnapshot;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GES-31: gêmeo digital em tempo real está FORA do escopo próprio.
 * O que esta tarefa entrega é a exportação para ferramenta de terceiro:
 * demanda por zona e por nó, consumo faturado agregado por zona e o
 * cadastro (contagens por papel), por conector de ARQUIVO (JSON/CSV
 * gravados em caminho dado) com o mesmo conteúdo exposto como DTO para
 * a API. Nenhum dado sai sem ser o da foto (topologia persistida +
 * consumo faturado do período).
 */
public final class DigitalTwinExport {

    private DigitalTwinExport() {
    }

    public record NodeDemand(String nodeCode, BigDecimal demandLps, BigDecimal billedVolumeM3) {
    }

    public record TwinExport(String zoneCode, String asOf, int billedPeriodDays,
                             BigDecimal zoneBilledVolumeM3, BigDecimal zoneDemandLps,
                             Map<String, NodeDemand> demandByNode,
                             Map<String, Long> registryByKind) {
    }

    public static TwinExport build(WaterSnapshot s) {
        Map<String, NodeDemand> byNode = new LinkedHashMap<>();
        BigDecimal zoneVolume = BigDecimal.ZERO;
        BigDecimal zoneDemand = BigDecimal.ZERO;
        for (WaterNode n : s.nodes()) {
            BigDecimal vol = s.billedVolumeM3ByNode().getOrDefault(n.code(), BigDecimal.ZERO);
            BigDecimal dem = s.demandLps(n.code()).stripTrailingZeros();
            byNode.put(n.code(), new NodeDemand(n.code(), dem, vol));
            zoneVolume = zoneVolume.add(vol);
            zoneDemand = zoneDemand.add(dem);
        }
        Map<String, Long> registry = new LinkedHashMap<>();
        for (WaterNode n : s.nodes()) registry.merge("NODE_" + n.kind().name(), 1L, Long::sum);
        s.arcs().forEach(a -> registry.merge("ARC_" + a.kind().name(), 1L, Long::sum));
        return new TwinExport(s.request().zoneCode(), s.request().asOf().toString(), s.billedPeriodDays(),
                zoneVolume, zoneDemand, byNode, registry);
    }

    /** JSON manual (sem dependência) com o conteúdo integral da exportação. */
    public static String toJson(TwinExport e) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"zoneCode\":\"").append(e.zoneCode()).append("\",\"asOf\":\"").append(e.asOf())
                .append("\",\"billedPeriodDays\":").append(e.billedPeriodDays())
                .append(",\"zoneBilledVolumeM3\":").append(e.zoneBilledVolumeM3().toPlainString())
                .append(",\"zoneDemandLps\":").append(e.zoneDemandLps().toPlainString())
                .append(",\"demandByNode\":{");
        boolean first = true;
        for (NodeDemand nd : e.demandByNode().values()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(nd.nodeCode()).append("\":{\"demandLps\":").append(nd.demandLps().toPlainString())
                    .append(",\"billedVolumeM3\":").append(nd.billedVolumeM3().toPlainString()).append('}');
        }
        sb.append("},\"registryByKind\":{");
        first = true;
        for (var en : e.registryByKind().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(en.getKey()).append("\":").append(en.getValue());
        }
        return sb.append("}}").toString();
    }

    public static String toCsv(TwinExport e) {
        StringBuilder sb = new StringBuilder("node_code;demand_lps;billed_volume_m3\n");
        for (NodeDemand nd : e.demandByNode().values()) {
            sb.append(nd.nodeCode()).append(';').append(nd.demandLps().toPlainString()).append(';')
                    .append(nd.billedVolumeM3().toPlainString()).append('\n');
        }
        return sb.toString();
    }

    /** Conector de arquivo: grava JSON e CSV no diretório dado e devolve os caminhos. */
    public static Map<String, Path> writeFiles(TwinExport e, Path directory) throws java.io.IOException {
        Files.createDirectories(directory);
        Path json = directory.resolve("gemeo-" + e.zoneCode() + "-" + e.asOf() + ".json");
        Path csv = directory.resolve("gemeo-" + e.zoneCode() + "-" + e.asOf() + ".csv");
        Files.writeString(json, toJson(e), StandardCharsets.UTF_8);
        Files.writeString(csv, toCsv(e), StandardCharsets.UTF_8);
        return Map.of("json", json, "csv", csv);
    }
}
