package br.com.ziipsan.intelligence.losses.anomaly;

import java.util.ArrayList;
import java.util.List;

/**
 * Estatistica robusta da linha de base (ED-94): mediana e MAD (desvio
 * absoluto mediano). Escolha deliberada e auditavel: mediana/MAD nao
 * sao arrastados pelo proprio outlier que se quer detectar, ao
 * contrario de media/desvio-padrao. O fator 1,4826 que converte o MAD
 * numa estimativa consistente do desvio-padrao (para distribuicao
 * normal) e uma constante MATEMATICA publicada — nao um limiar de
 * dominio: o limiar de disparo e sempre o {@code deviationK} do
 * parametro {@code anomaly.sensitivity} do tenant.
 */
public final class BaselineStatistics {

    /** Fator de consistencia MAD -&gt; sigma (constante matematica, nao limiar). */
    public static final double MAD_TO_SIGMA = 1.4826;

    private BaselineStatistics() {}

    /** Mediana (media dos dois centrais quando a contagem e par). */
    public static double median(List<Double> values) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("mediana exige ao menos um valor");
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** MAD: mediana dos desvios absolutos em torno da mediana. */
    public static double mad(List<Double> values, double median) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("MAD exige ao menos um valor");
        List<Double> deviations = new ArrayList<>(values.size());
        for (double v : values) deviations.add(Math.abs(v - median));
        return median(deviations);
    }

    /** Sigma robusto estimado a partir do MAD. */
    public static double robustSigma(double mad) {
        if (mad < 0 || Double.isNaN(mad))
            throw new IllegalArgumentException("MAD invalido: " + mad);
        return MAD_TO_SIGMA * mad;
    }
}
