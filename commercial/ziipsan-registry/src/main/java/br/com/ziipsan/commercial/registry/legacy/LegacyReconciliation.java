package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conciliação automática (P1.2): totais do legado contra totais do
 * staging, por setor, rota, categoria e saldo.
 *
 * <p>Os totais do legado ({@link LegacyTotals}) vêm com a carga — são os
 * totais do próprio sistema legado (relatório de fechamento dele), não
 * números digitados por quem importa. A conciliação compara o que o
 * staging encenou com o que o legado declarou: divergência é achado, não
 * ajuste automático. O saldo é conciliado no total e por setor; a
 * contagem, por setor, rota e categoria. Chave ausente de um lado vira
 * linha com o outro lado zerado, para a divergência aparecer.
 */
public final class LegacyReconciliation {

    private LegacyReconciliation() {
    }

    /** Totais declarados pelo legado, entregues com a carga. */
    public record LegacyTotals(
            Map<String, Long> sectorCounts,
            Map<String, Long> routeCounts,
            Map<String, Long> categoryCounts,
            long balanceTotalCents,
            Map<String, Long> balanceBySectorCents) {
        public LegacyTotals {
            sectorCounts = sectorCounts == null ? Map.of() : Map.copyOf(sectorCounts);
            routeCounts = routeCounts == null ? Map.of() : Map.copyOf(routeCounts);
            categoryCounts = categoryCounts == null ? Map.of() : Map.copyOf(categoryCounts);
            balanceBySectorCents = balanceBySectorCents == null ? Map.of() : Map.copyOf(balanceBySectorCents);
        }
    }

    public enum Dimension { SECTOR, ROUTE, CATEGORY, BALANCE, BALANCE_BY_SECTOR }

    public record Row(
            Dimension dimension,
            String key,
            Long legacyCount,
            Long stagedCount,
            Long legacyAmountCents,
            Long stagedAmountCents,
            boolean matches) {
    }

    public record Report(List<Row> rows, boolean allMatch) {
    }

    public static Report reconcile(List<StagedUnit> staged, LegacyTotals legacy) {
        List<StagedUnit> rows = staged == null ? List.of() : staged;
        List<Row> out = new ArrayList<>();

        Map<String, Long> sectorCounts = new LinkedHashMap<>();
        Map<String, Long> routeCounts = new LinkedHashMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        Map<String, Long> balanceBySector = new LinkedHashMap<>();
        long balanceTotal = 0;
        for (StagedUnit unit : rows) {
            if (unit.sectorCode() != null) {
                sectorCounts.merge(unit.sectorCode(), 1L, Long::sum);
            }
            if (unit.routeCode() != null) {
                routeCounts.merge(unit.routeCode(), 1L, Long::sum);
            }
            if (unit.categoryCode() != null) {
                categoryCounts.merge(unit.categoryCode(), 1L, Long::sum);
            }
            if (unit.balanceCents() != null) {
                balanceTotal += unit.balanceCents();
                if (unit.sectorCode() != null) {
                    balanceBySector.merge(unit.sectorCode(), unit.balanceCents(), Long::sum);
                }
            }
        }
        compareCounts(out, Dimension.SECTOR, legacy.sectorCounts(), sectorCounts);
        compareCounts(out, Dimension.ROUTE, legacy.routeCounts(), routeCounts);
        compareCounts(out, Dimension.CATEGORY, legacy.categoryCounts(), categoryCounts);
        out.add(new Row(Dimension.BALANCE, "TOTAL", null, null,
                legacy.balanceTotalCents(), balanceTotal, legacy.balanceTotalCents() == balanceTotal));
        compareAmounts(out, Dimension.BALANCE_BY_SECTOR, legacy.balanceBySectorCents(), balanceBySector);

        boolean allMatch = out.stream().allMatch(Row::matches);
        return new Report(List.copyOf(out), allMatch);
    }

    private static void compareCounts(List<Row> out, Dimension dimension,
                                      Map<String, Long> legacy, Map<String, Long> staged) {
        Map<String, Long> keys = new LinkedHashMap<>();
        legacy.keySet().forEach(key -> keys.put(key, 0L));
        staged.keySet().forEach(key -> keys.putIfAbsent(key, 0L));
        for (String key : keys.keySet()) {
            Long l = legacy.getOrDefault(key, 0L);
            Long s = staged.getOrDefault(key, 0L);
            out.add(new Row(dimension, key, l, s, null, null, l.equals(s)));
        }
    }

    private static void compareAmounts(List<Row> out, Dimension dimension,
                                       Map<String, Long> legacy, Map<String, Long> staged) {
        Map<String, Long> keys = new LinkedHashMap<>();
        legacy.keySet().forEach(key -> keys.put(key, 0L));
        staged.keySet().forEach(key -> keys.putIfAbsent(key, 0L));
        for (String key : keys.keySet()) {
            Long l = legacy.getOrDefault(key, 0L);
            Long s = staged.getOrDefault(key, 0L);
            out.add(new Row(dimension, key, null, null, l, s, l.equals(s)));
        }
    }
}
