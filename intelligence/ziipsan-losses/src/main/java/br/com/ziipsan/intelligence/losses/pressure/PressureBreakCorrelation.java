package br.com.ziipsan.intelligence.losses.pressure;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Relacao entre pressao e rompimentos por arco (ED-86, OPE-04): por
 * arco (trecho de rede), a pressao media/maxima do periodo e os
 * rompimentos contados nele. O metodo declarado e a correlacao de
 * Pearson entre a pressao MEDIA do arco e a TAXA de rompimentos
 * (rompimentos por km), calculada so sobre arcos com extensao
 * conhecida; com menos de 2 arcos ou variancia nula em qualquer
 * eixo, a correlacao fica AUSENTE (nunca um numero inventado).
 * Correlacao nao e causalidade: o resultado expoe tambem os arcos
 * ordenados pela taxa para a leitura do gestor.
 */
public final class PressureBreakCorrelation {

    public record ArcObservation(String arcId, double meanPressureMca, double maxPressureMca,
                                 int breakCount, Double lengthKmOrNull) {
        public ArcObservation {
            if (arcId == null || arcId.isBlank()) throw new IllegalArgumentException("arco obrigatorio");
            if (meanPressureMca < 0 || Double.isNaN(meanPressureMca))
                throw new IllegalArgumentException("pressao media do arco invalida: " + meanPressureMca);
            if (maxPressureMca < meanPressureMca || Double.isNaN(maxPressureMca))
                throw new IllegalArgumentException("pressao maxima do arco menor que a media: " + maxPressureMca);
            if (breakCount < 0) throw new IllegalArgumentException("rompimentos do arco negativos");
            if (lengthKmOrNull != null && lengthKmOrNull <= 0)
                throw new IllegalArgumentException("extensao do arco deve ser positiva quando conhecida: " + lengthKmOrNull);
        }
        /** Taxa de rompimentos por km; vazia sem extensao conhecida (nunca estimada). */
        public OptionalDouble breaksPerKm() {
            return lengthKmOrNull == null ? OptionalDouble.empty()
                    : OptionalDouble.of(breakCount / lengthKmOrNull);
        }
    }

    public record Result(int arcs, int arcsWithLength, long totalBreaks,
                         OptionalDouble pearsonMeanPressureVsBreaksPerKm,
                         List<ArcObservation> rankedByBreakRatePerKm) {}

    private PressureBreakCorrelation() {}

    public static Result correlate(List<ArcObservation> observations) {
        Objects.requireNonNull(observations, "observacoes por arco obrigatorias");
        if (observations.isEmpty()) throw new IllegalArgumentException(
                "relacao pressao x rompimentos exige ao menos um arco observado");
        List<ArcObservation> withLength = observations.stream()
                .filter(o -> o.breaksPerKm().isPresent()).toList();
        List<ArcObservation> ranked = withLength.stream()
                .sorted(Comparator.comparingDouble((ArcObservation o) -> o.breaksPerKm().getAsDouble())
                        .reversed().thenComparing(ArcObservation::arcId))
                .toList();
        OptionalDouble pearson = OptionalDouble.empty();
        if (withLength.size() >= 2) {
            double[] xs = withLength.stream().mapToDouble(ArcObservation::meanPressureMca).toArray();
            double[] ys = withLength.stream().mapToDouble(o -> o.breaksPerKm().getAsDouble()).toArray();
            pearson = pearson(xs, ys);
        }
        long total = observations.stream().mapToLong(ArcObservation::breakCount).sum();
        return new Result(observations.size(), withLength.size(), total, pearson, ranked);
    }

    /** Pearson classico; vazio quando qualquer eixo tem variancia nula. */
    static OptionalDouble pearson(double[] xs, double[] ys) {
        int n = xs.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += xs[i]; my += ys[i]; }
        mx /= n; my /= n;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - mx, dy = ys[i] - my;
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy;
        }
        if (sxx == 0 || syy == 0) return OptionalDouble.empty();
        return OptionalDouble.of(sxy / Math.sqrt(sxx * syy));
    }
}
