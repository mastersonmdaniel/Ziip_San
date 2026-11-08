package br.com.ziipsan.core.incident;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relatorio de ocorrencias por causa, tempo de reparo e volume
 * perdido (JOR-33) — so ocorrencias ENCERRADAS entram (a causa so
 * existe no encerramento); tempo/volume desconhecidos (nulos na OS)
 * contam como desconhecidos, nunca como zero.
 */
public final class IncidentReport {

    /**
     * Linha do JOR-33. Correcao M2 da revisao da ED-92: total e media
     * sao {@link Double} ANULAVEIS — quando nenhum valor da causa e
     * conhecido ({@code volumeKnown==0} / {@code repairKnown==0}), o
     * campo e {@code null} (AUSENTE), nunca {@code 0.0}: desconhecido
     * nao vira zero, espelhando o {@code SUM}/{@code AVG} da view SQL.
     * Os contadores de conhecidos acompanham para distinguir total
     * parcial (mistura conhecido+desconhecido) de total completo.
     */
    public record CauseRow(String causeCode, long incidents, long repairKnown,
                           Double avgRepairMinutesKnown, long volumeKnown, Double totalLostVolumeM3) {}

    private IncidentReport() {}

    public static List<CauseRow> byCause(List<NetworkIncident> incidents) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, double[]> sums = new LinkedHashMap<>();
        for (NetworkIncident i : incidents) {
            if (i.status() != NetworkIncident.Status.CLOSED || i.closure() == null) continue;
            String cause = i.closure().causeCode();
            long[] c = counts.computeIfAbsent(cause, k -> new long[3]);
            double[] s = sums.computeIfAbsent(cause, k -> new double[2]);
            c[0]++;
            if (i.closure().repairMinutesOrNull() != null) { c[1]++; s[0] += i.closure().repairMinutesOrNull(); }
            if (i.closure().lostVolumeM3OrNull() != null) { c[2]++; s[1] += i.closure().lostVolumeM3OrNull(); }
        }
        return counts.entrySet().stream().map(e -> {
            long[] c = e.getValue(); double[] s = sums.get(e.getKey());
            return new CauseRow(e.getKey(), c[0], c[1],
                    c[1] == 0 ? null : s[0] / c[1],
                    c[2],
                    c[2] == 0 ? null : s[1]);
        }).toList();
    }
}
