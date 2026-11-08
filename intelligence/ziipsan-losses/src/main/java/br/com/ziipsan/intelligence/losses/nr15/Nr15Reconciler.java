package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.Objects;

/**
 * Reconciliacao do balanco publicado da NR 15 com o balanco por DMC
 * da ED-85 do mesmo periodo (ED-78; item literal do aceite: "teste
 * que reconcilia o balanco publicado com o balanco por DMC da ED-85
 * do mesmo periodo").
 *
 * <p>A reconciliacao compara, componente a componente, o balanco
 * padronizado (publicado) com a SOMA das fotos da ED-85 do ano:
 * entrada, exportado, entrada liquida, faturado micromedido,
 * faturado nao micromedido, faturado total, autorizado nao faturado,
 * consumo autorizado, perdas totais, perdas aparentes, perdas reais
 * e agua nao faturada. Soma e a unica operacao - nenhum componente e
 * recalculado a partir de insumos.
 *
 * <p>{@link #TOLERANCE_M3} (0,01 m3) e tolerancia ARITMETICA de
 * arredondamento desta entrega (somas em ponto flutuante sobre
 * valores gravados), declarada como escolha de implementacao - NAO
 * e limiar de norma: nenhum valor da Res. ANA 275/2025 e usado como
 * tolerancia ou meta nesta entrega ([SEM FONTE] para limiares).
 */
public final class Nr15Reconciler {

    /** Tolerancia aritmetica de reconciliacao, em m3 (nao e norma). */
    public static final double TOLERANCE_M3 = 0.01;

    /** Diferenca de um componente: publicado - soma da ED-85. */
    public record ComponentDiff(String component, Double publishedOrNull, Double dmcSumOrNull,
                                Double differenceOrNull) {
        public boolean matches() {
            if (publishedOrNull == null || dmcSumOrNull == null) {
                // Desconhecido dos dois lados reconcilia; de um so, nao.
                return publishedOrNull == null && dmcSumOrNull == null;
            }
            return differenceOrNull != null && Math.abs(differenceOrNull) <= TOLERANCE_M3;
        }
    }

    public record ReconciliationResult(int baseYear, List<ComponentDiff> components) {
        public ReconciliationResult {
            components = List.copyOf(components);
        }

        public boolean reconciled() {
            return components.stream().allMatch(ComponentDiff::matches);
        }

        public List<ComponentDiff> divergences() {
            return components.stream().filter(c -> !c.matches()).toList();
        }
    }

    private Nr15Reconciler() {
    }

    public static ReconciliationResult reconcile(StandardizedWaterBalance published,
                                                 List<DmcBalanceSnapshot> dmcSnapshots) {
        Objects.requireNonNull(published, "balanco publicado obrigatorio na reconciliacao");
        Objects.requireNonNull(dmcSnapshots, "fotos da ED-85 obrigatorias na reconciliacao");
        if (dmcSnapshots.isEmpty()) {
            throw new IllegalArgumentException("reconciliacao sem nenhuma foto da ED-85");
        }
        for (DmcBalanceSnapshot snapshot : dmcSnapshots) {
            if (snapshot.period().getYear() != published.baseYear()) {
                throw new IllegalArgumentException("foto da ED-85 fora do ano do balanco publicado: "
                        + snapshot.period() + " vs " + published.baseYear());
            }
        }
        List<ComponentDiff> diffs = List.of(
                diff("systemInputM3", published.systemInputM3(),
                        sum(dmcSnapshots, s -> s.systemInputM3())),
                diff("exportedM3", published.exportedM3(),
                        sum(dmcSnapshots, s -> s.exportedM3())),
                diff("netInputM3", published.netInputM3(),
                        sum(dmcSnapshots, s -> s.netInputM3())),
                diff("billedMeteredM3", published.billedMeteredM3(),
                        sum(dmcSnapshots, s -> s.billedMeteredAdjustedM3())),
                diff("billedUnmeteredM3", published.billedUnmeteredM3(),
                        sum(dmcSnapshots, s -> s.billedUnmeteredM3())),
                diff("billedTotalM3", published.billedTotalM3(),
                        sum(dmcSnapshots, s -> s.billedTotalM3())),
                diff("authorizedUnbilledM3", published.authorizedUnbilledM3(),
                        sum(dmcSnapshots, s -> s.authorizedUnbilledM3())),
                diff("authorizedConsumptionM3", published.authorizedConsumptionM3(),
                        sum(dmcSnapshots, s -> s.authorizedConsumptionM3())),
                diff("totalLossesM3", published.totalLossesM3(),
                        sum(dmcSnapshots, s -> s.totalLossesM3())),
                diffNullable("apparentLossesM3", published.apparentLossesM3OrNull(),
                        sumNullable(dmcSnapshots, true)),
                diffNullable("realLossesM3", published.realLossesM3OrNull(),
                        sumNullable(dmcSnapshots, false)),
                diff("nonRevenueWaterM3", published.nonRevenueWaterM3(),
                        sum(dmcSnapshots, s -> s.nonRevenueWaterM3())));
        return new ReconciliationResult(published.baseYear(), diffs);
    }

    private interface Extractor {
        double get(DmcBalanceSnapshot snapshot);
    }

    private static double sum(List<DmcBalanceSnapshot> snapshots, Extractor extractor) {
        return snapshots.stream().mapToDouble(extractor::get).sum();
    }

    /** Soma anulavel: se QUALQUER foto desconhece o componente, a soma e desconhecida. */
    private static Double sumNullable(List<DmcBalanceSnapshot> snapshots, boolean apparent) {
        double total = 0.0;
        for (DmcBalanceSnapshot snapshot : snapshots) {
            Double value = apparent ? snapshot.apparentLossesM3OrNull() : snapshot.realLossesM3OrNull();
            if (value == null) return null;
            total += value;
        }
        return total;
    }

    private static ComponentDiff diff(String component, double published, double dmcSum) {
        return new ComponentDiff(component, published, dmcSum, published - dmcSum);
    }

    private static ComponentDiff diffNullable(String component, Double published, Double dmcSum) {
        Double difference = (published == null || dmcSum == null) ? null : published - dmcSum;
        return new ComponentDiff(component, published, dmcSum, difference);
    }
}
