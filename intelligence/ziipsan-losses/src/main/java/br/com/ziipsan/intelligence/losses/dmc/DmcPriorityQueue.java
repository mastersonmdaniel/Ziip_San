package br.com.ziipsan.intelligence.losses.dmc;

import java.util.Comparator;
import java.util.List;

/**
 * Fila de DMC por prioridade (ED-85; OPE-01): ordena os DMCs pelo
 * EXCESSO de perdas reais sobre a meta do tenant
 * ({@code loss.dmc_target_l_per_conn_day}), em L/ligacao/dia, do
 * maior para o menor; desempate pelo ILI (maior primeiro, ausente por
 * ultimo). DMC sem perda real conhecida ou sem meta NAO entra na
 * fila — ranquear sem os dois numeros seria inventar prioridade; os
 * excluidos sao contados para o gestor completar os dados.
 */
public final class DmcPriorityQueue {

    public record Ranked(WaterBalanceCalculator.DmcMonthlyBalance balance,
                         double excessLPerConnDay, Double iliOrNull) {}

    public record Queue(List<Ranked> ranked, long excludedWithoutRealLosses, long excludedWithoutTarget) {}

    private DmcPriorityQueue() {}

    public static Queue rank(List<WaterBalanceCalculator.DmcMonthlyBalance> balances,
                             java.util.Map<java.util.UUID, Double> targetsByDmc) {
        long withoutReal = balances.stream().filter(b -> b.realLossesLPerConnDayOrNull() == null).count();
        List<WaterBalanceCalculator.DmcMonthlyBalance> withReal = balances.stream()
                .filter(b -> b.realLossesLPerConnDayOrNull() != null).toList();
        long withoutTarget = withReal.stream().filter(b -> targetsByDmc.get(b.dmcId()) == null).count();
        List<Ranked> ranked = withReal.stream()
                .filter(b -> targetsByDmc.get(b.dmcId()) != null)
                .map(b -> new Ranked(b, b.realLossesLPerConnDayOrNull() - targetsByDmc.get(b.dmcId()),
                        b.iliOrNull()))
                .sorted(Comparator
                        .comparingDouble(Ranked::excessLPerConnDay).reversed()
                        .thenComparing(r -> r.iliOrNull() == null ? Double.NEGATIVE_INFINITY : r.iliOrNull(),
                                Comparator.reverseOrder()))
                .toList();
        return new Queue(ranked, withoutReal, withoutTarget);
    }
}
