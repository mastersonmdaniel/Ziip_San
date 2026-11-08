package br.com.ziipsan.intelligence.losses.anomaly;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Motor de deteccao da ED-94 (OPE-36): aplica SOMENTE as regras
 * ligadas em {@code anomaly.rules_enabled}, com os limiares de
 * {@code anomaly.sensitivity}, e devolve alertas {@code PROPOSED}.
 *
 * <p><b>Confianca (metodo interno declarado, nao norma):</b> toda
 * regra produz confianca em [0,1] com a mesma forma auditavel —
 * 0,5 no limiar exato de disparo, caminhando linearmente ate 1,0
 * quando a grandeza observada dobra o limiar; a regra de desvio
 * multiplica ainda pelo fator de cobertura da base
 * {@code min(1, amostrasDaBase / (2 x minBaselineSamples))}, para que
 * base historica curta nunca produza confianca cheia. A formula esta
 * nos javadocs de cada regra e os testes a fixam com numeros exatos.
 *
 * <p><b>ADR-0042:</b> este motor nao conhece porta de acao nenhuma —
 * nao ha como ele abrir OS, pesquisa ou qualquer efeito. Ele propoe;
 * {@link AnomalyReviewService} e o unico caminho para acao, e exige
 * revisao humana registrada.
 */
public final class AnomalyDetector {

    private AnomalyDetector() {}

    /** Avalia a serie inteira e devolve os alertas em ordem cronologica de deteccao. */
    public static List<AnomalyAlert> evaluate(AnomalySeries series,
                                              AnomalyParameters.AnomalyConfig config) {
        Objects.requireNonNull(series, "serie obrigatoria");
        Objects.requireNonNull(config, "configuracao (anomaly.*) obrigatoria");
        List<AnomalyAlert> alerts = new ArrayList<>();
        if (config.isEnabled(AnomalyRule.SAME_HOUR_WEEKDAY_DEVIATION)
                && AnomalyRule.SAME_HOUR_WEEKDAY_DEVIATION.appliesTo(series.kind()))
            alerts.addAll(deviationAlerts(series, config));
        if (config.isEnabled(AnomalyRule.CONTINUOUS_CONSUMPTION)
                && AnomalyRule.CONTINUOUS_CONSUMPTION.appliesTo(series.kind()))
            alerts.addAll(runAlerts(series, config, true));
        if (config.isEnabled(AnomalyRule.ZERO_CONSUMPTION)
                && AnomalyRule.ZERO_CONSUMPTION.appliesTo(series.kind()))
            alerts.addAll(runAlerts(series, config, false));
        if (config.isEnabled(AnomalyRule.SUDDEN_PRESSURE_DROP)
                && AnomalyRule.SUDDEN_PRESSURE_DROP.appliesTo(series.kind()))
            alerts.addAll(pressureDropAlerts(series, config));
        alerts.sort(java.util.Comparator.comparing(AnomalyAlert::detectedAt)
                .thenComparing(a -> a.rule().name()));
        return List.copyOf(alerts);
    }

    // ------------------------------------------------------------------
    // Regra 1: desvio contra a mesma hora e o mesmo dia da semana
    // ------------------------------------------------------------------

    /**
     * Para cada ponto, a base e formada pelos pontos ANTERIORES da
     * propria serie no mesmo slot (mesmo dia da semana e mesma HORA
     * DO DIA — {@code getHour()}, correcao R4 da revisao cruzada: o
     * aceite diz "mesma hora", nao o {@code LocalTime} exato; um alvo
     * as 03:30 usa a base das 03:00), em data diferente. A base NAO
     * tem teto nem paginacao: entram TODOS os pontos anteriores do
     * slot, por maior que seja o historico (sem perda silenciosa).
     * Com menos de {@code minBaselineSamples}
     * amostras na base, o ponto nao e julgado (sem base, sem alerta —
     * nunca se presume). Dispara quando |valor - mediana| atinge
     * {@code deviationK} sigmas robustos (MAD). Se o MAD e zero (base
     * perfeitamente estavel), qualquer valor diferente da mediana
     * dispara, com o desvio reportado como ausente (divisao por zero
     * nao se disfarca) e a forca tratada como maxima na confianca.
     */
    static List<AnomalyAlert> deviationAlerts(AnomalySeries series,
                                              AnomalyParameters.AnomalyConfig config) {
        var sensitivity = config.sensitivity();
        List<AnomalyAlert> out = new ArrayList<>();
        List<AnomalySeries.SeriesPoint> points = series.points();
        for (int i = 0; i < points.size(); i++) {
            AnomalySeries.SeriesPoint target = points.get(i);
            List<AnomalySeries.SeriesPoint> baselinePoints = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                AnomalySeries.SeriesPoint candidate = points.get(j);
                if (candidate.at().getDayOfWeek() == target.at().getDayOfWeek()
                        && candidate.at().getHour() == target.at().getHour()
                        && !candidate.at().toLocalDate().equals(target.at().toLocalDate()))
                    baselinePoints.add(candidate);
            }
            if (baselinePoints.size() < sensitivity.minBaselineSamples()) continue;
            List<Double> values = baselinePoints.stream().map(AnomalySeries.SeriesPoint::value).toList();
            double median = BaselineStatistics.median(values);
            double mad = BaselineStatistics.mad(values, median);
            double sigma = BaselineStatistics.robustSigma(mad);
            double distance = Math.abs(target.value() - median);
            boolean fires;
            Double deviationSigma;
            double strength;
            if (sigma == 0.0) {
                fires = distance > 0;
                deviationSigma = null;
                strength = 1.0;
            } else {
                double z = distance / sigma;
                deviationSigma = z;
                fires = z >= sensitivity.deviationK();
                strength = 0.5 + 0.5 * Math.min(1.0, Math.max(0.0,
                        (z - sensitivity.deviationK()) / sensitivity.deviationK()));
            }
            if (!fires) continue;
            double coverage = Math.min(1.0,
                    baselinePoints.size() / (2.0 * sensitivity.minBaselineSamples()));
            double confidence = coverage * strength;
            List<AnomalySeries.SeriesPoint> evidence = new ArrayList<>(baselinePoints);
            evidence.add(target);
            String explanation = String.format(Locale.ROOT,
                    "Desvio na mesma hora/dia da semana: valor %s %s em %s; base de %d amostras "
                            + "(mediana %s, sigma robusto %s); desvio %s; limiar %s sigmas.",
                    fmt(target.value()), series.unit(), target.at(), baselinePoints.size(),
                    fmt(median), sigma == 0.0 ? "0 (base sem dispersao)" : fmt(sigma),
                    deviationSigma == null ? "nao definido (sigma zero)" : fmt(deviationSigma) + " sigmas",
                    fmt(sensitivity.deviationK()));
            out.add(new AnomalyAlert(series.seriesRef(), series.kind(), series.unit(),
                    AnomalyRule.SAME_HOUR_WEEKDAY_DEVIATION, target.at(),
                    baselinePoints.get(0).at(), target.at(),
                    target.value(), median, sigma, deviationSigma,
                    "deviationK=" + fmt(sensitivity.deviationK())
                            + " sigmas robustos (MAD); minBaselineSamples=" + sensitivity.minBaselineSamples(),
                    confidence, explanation, evidence, config.snapshot(),
                    AnomalyAlert.Status.PROPOSED));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Regras 2 e 3: corridas contiguas (consumo continuo / consumo zero)
    // ------------------------------------------------------------------

    /**
     * Uma corrida e uma sequencia de amostras HORARIAS CONTIGUAS (cada
     * uma exatamente 1h apos a anterior — buraco quebra a corrida, o
     * mesmo criterio da ED-17) satisfazendo o predicado (&gt; 0 para
     * continuo; == 0 para zero). Cada corrida MAXIMA que atinge o
     * comprimento minimo do parametro gera UM alerta, no ultimo ponto
     * da corrida, com a corrida inteira como evidencia.
     */
    static List<AnomalyAlert> runAlerts(AnomalySeries series,
                                        AnomalyParameters.AnomalyConfig config,
                                        boolean continuous) {
        var sensitivity = config.sensitivity();
        int minRun = continuous ? sensitivity.continuousHours() : sensitivity.zeroHours();
        AnomalyRule rule = continuous ? AnomalyRule.CONTINUOUS_CONSUMPTION : AnomalyRule.ZERO_CONSUMPTION;
        List<AnomalyAlert> out = new ArrayList<>();
        List<AnomalySeries.SeriesPoint> points = series.points();
        int i = 0;
        while (i < points.size()) {
            if (!matches(points.get(i), continuous)) { i++; continue; }
            int start = i;
            while (i + 1 < points.size()
                    && matches(points.get(i + 1), continuous)
                    && points.get(i + 1).at().equals(points.get(i).at().plusHours(1)))
                i++;
            int end = i;
            int runLength = end - start + 1;
            if (runLength >= minRun) {
                List<AnomalySeries.SeriesPoint> run = points.subList(start, end + 1);
                double confidence = 0.5 + 0.5 * Math.min(1.0,
                        (runLength - minRun) / (double) minRun);
                AnomalySeries.SeriesPoint last = points.get(end);
                String explanation = continuous
                        ? String.format(Locale.ROOT,
                                "Consumo continuo: %d horas seguidas com consumo > 0 (%s a %s); limiar %d horas.",
                                runLength, points.get(start).at(), last.at(), minRun)
                        : String.format(Locale.ROOT,
                                "Consumo zero: %d horas seguidas com consumo = 0 (%s a %s); limiar %d horas.",
                                runLength, points.get(start).at(), last.at(), minRun);
                out.add(new AnomalyAlert(series.seriesRef(), series.kind(), series.unit(),
                        rule, last.at(), points.get(start).at(), last.at(),
                        last.value(), null, null, null,
                        (continuous ? "continuousHours=" : "zeroHours=") + minRun + " horas contiguas",
                        confidence, explanation, run, config.snapshot(),
                        AnomalyAlert.Status.PROPOSED));
            }
            i++;
        }
        return out;
    }

    private static boolean matches(AnomalySeries.SeriesPoint point, boolean continuous) {
        return continuous ? point.value() > 0 : point.value() == 0.0;
    }

    // ------------------------------------------------------------------
    // Regra 4: queda subita de pressao
    // ------------------------------------------------------------------

    /**
     * Para cada par de amostras consecutivas da serie, a queda relativa
     * {@code (anterior - atual) / anterior} que atinge
     * {@code pressureDropPercent} dispara um alerta na segunda amostra,
     * com o par como evidencia. Amostra anterior zero nao define queda
     * relativa (divisao por zero nao se disfarca): o par e ignorado.
     *
     * <p><b>Adjacencia temporal (correcao R2 da revisao cruzada):</b>
     * "subita" exige amostras ADJACENTES no tempo — o par so e
     * comparado quando a segunda amostra esta exatamente 1h depois da
     * primeira (o passo esperado das series horarias da ED-17, o mesmo
     * criterio de contiguidade das regras de corrida). Par espaçado
     * (buraco na serie, amostras a dias de distancia) e ignorado: uma
     * queda lenta ou remota nao e queda subita.
     */
    static List<AnomalyAlert> pressureDropAlerts(AnomalySeries series,
                                                 AnomalyParameters.AnomalyConfig config) {
        var sensitivity = config.sensitivity();
        double threshold = sensitivity.pressureDropFraction();
        List<AnomalyAlert> out = new ArrayList<>();
        List<AnomalySeries.SeriesPoint> points = series.points();
        for (int i = 1; i < points.size(); i++) {
            AnomalySeries.SeriesPoint prev = points.get(i - 1);
            AnomalySeries.SeriesPoint curr = points.get(i);
            if (!curr.at().equals(prev.at().plusHours(1))) continue;
            if (prev.value() <= 0) continue;
            double dropFraction = (prev.value() - curr.value()) / prev.value();
            if (dropFraction < threshold) continue;
            double confidence = 0.5 + 0.5 * Math.min(1.0,
                    (dropFraction - threshold) / threshold);
            String explanation = String.format(Locale.ROOT,
                    "Queda subita de pressao: de %s %s em %s para %s %s em %s (queda de %s%%); limiar %s%%.",
                    fmt(prev.value()), series.unit(), prev.at(),
                    fmt(curr.value()), series.unit(), curr.at(),
                    fmt(dropFraction * 100.0), fmt(sensitivity.pressureDropPercent()));
            out.add(new AnomalyAlert(series.seriesRef(), series.kind(), series.unit(),
                    AnomalyRule.SUDDEN_PRESSURE_DROP, curr.at(), prev.at(), curr.at(),
                    curr.value(), null, null, null,
                    "pressureDropPercent=" + fmt(sensitivity.pressureDropPercent()) + "% entre amostras consecutivas (passo de 1h)",
                    confidence, explanation, List.of(prev, curr), config.snapshot(),
                    AnomalyAlert.Status.PROPOSED));
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }
}
