package br.com.ziipsan.platform.portal.selfcare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Comparativo OPCIONAL com domicilios semelhantes (ED-38):
 * {@code bill.peer_comparison} DESLIGADO por padrao — desligado, nada e
 * calculado nem exibido. Ligado, o comparativo so sai com agregado de no
 * minimo {@code bill.peer_min_units} domicilios, por analogia a
 * {@code gis.public_aggregation_min_units} (piso 5, classe c, so amplia):
 * minimo configurado abaixo de 5 e RECUSADO, e coorte abaixo do minimo
 * vigente NAO e publicada (nem media, nem contagem exata alem do minimo).
 * Nenhum dado individual de terceiro e exposto: so media e mediana da
 * coorte anonima.
 */
public final class PeerComparison {

    /** Piso do agregado minimo, por analogia ao gis.public_aggregation_min_units (decisao do dono, v2.11). */
    public static final int MIN_UNITS_FLOOR = 5;

    public record Result(boolean published, BigDecimal cohortAverageM3, BigDecimal cohortMedianM3,
                         BigDecimal ownConsumptionM3, String position) { }

    private final SelfCareParameters params;

    public PeerComparison(SelfCareParameters params) {
        if (params == null) {
            throw new IllegalArgumentException("parametros sao obrigatorios");
        }
        this.params = params;
    }

    public int effectiveMinUnits(UUID tenantId) {
        int configured = params.peerMinUnits(tenantId).orElse(MIN_UNITS_FLOOR);
        if (configured < MIN_UNITS_FLOOR) {
            throw new IllegalStateException(
                    "bill.peer_min_units abaixo do piso " + MIN_UNITS_FLOOR + " (classe c, so amplia): recusado");
        }
        return configured;
    }

    public Result compare(UUID tenantId, BigDecimal ownConsumptionM3, List<BigDecimal> cohortConsumptionM3) {
        if (ownConsumptionM3 == null || cohortConsumptionM3 == null) {
            throw new IllegalArgumentException("consumo proprio e coorte sao obrigatorios");
        }
        if (!params.peerComparisonEnabled(tenantId)) {
            return new Result(false, null, null, ownConsumptionM3, "DISABLED");
        }
        int min = effectiveMinUnits(tenantId);
        if (cohortConsumptionM3.size() < min) {
            return new Result(false, null, null, ownConsumptionM3, "SUPPRESSED_BELOW_MIN_UNITS");
        }
        List<BigDecimal> sorted = cohortConsumptionM3.stream().sorted().toList();
        BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(sorted.size()), 4, RoundingMode.HALF_UP);
        int n = sorted.size();
        BigDecimal median = n % 2 == 1
                ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
        String position = ownConsumptionM3.compareTo(avg) < 0 ? "BELOW_AVERAGE"
                : ownConsumptionM3.compareTo(avg) > 0 ? "ABOVE_AVERAGE" : "AT_AVERAGE";
        return new Result(true, avg, median, ownConsumptionM3, position);
    }
}
