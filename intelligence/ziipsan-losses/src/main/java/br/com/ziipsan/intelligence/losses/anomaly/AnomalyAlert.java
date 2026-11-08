package br.com.ziipsan.intelligence.losses.anomaly;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Alerta de anomalia EXPLICAVEL (ED-94; OPE-36; ADR-0042). O aceite
 * exige que cada alerta mostre A SERIE, A REGRA E A CONFIANCA — este
 * registro carrega os tres, mais o retrato numerico completo da
 * comparacao (observado, mediana da base, dispersao, desvio, limiar
 * usado e o snapshot dos parametros), para um humano reproduzir a
 * conta com papel e lapis.
 *
 * <p><b>Fronteira dura do ADR-0042:</b> o alerta nasce SEMPRE
 * {@link Status#PROPOSED}. Ele nao e uma decisao, nao abre OS nem
 * pesquisa e nao tem referencia a nenhum mecanismo de acao: a unica
 * saida dele e a revisao humana de {@link AnomalyReviewService}.
 */
public record AnomalyAlert(
        String seriesRef,
        AnomalySeries.SeriesKind seriesKind,
        String unit,
        AnomalyRule rule,
        LocalDateTime detectedAt,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        double observedValue,
        Double baselineMedian,
        Double baselineSigma,
        Double deviationSigma,
        String thresholdDescription,
        double confidence,
        String explanation,
        List<AnomalySeries.SeriesPoint> evidence,
        String parameterSnapshot,
        Status status) {

    /** Situacao do alerta: nasce PROPOSED; CONFIRMED/DISMISSED so existem via revisao humana. */
    public enum Status { PROPOSED, CONFIRMED, DISMISSED }

    public AnomalyAlert {
        if (seriesRef == null || seriesRef.isBlank())
            throw new IllegalArgumentException("alerta exige a referencia da serie");
        Objects.requireNonNull(seriesKind, "tipo da serie obrigatorio");
        Objects.requireNonNull(rule, "regra do alerta obrigatoria");
        Objects.requireNonNull(detectedAt, "instante da deteccao obrigatorio");
        Objects.requireNonNull(windowStart, "inicio da janela obrigatorio");
        Objects.requireNonNull(windowEnd, "fim da janela obrigatorio");
        if (windowEnd.isBefore(windowStart))
            throw new IllegalArgumentException("janela do alerta termina antes de comecar");
        if (Double.isNaN(confidence) || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("confianca fora de [0,1]: " + confidence);
        if (explanation == null || explanation.isBlank())
            throw new IllegalArgumentException("alerta explicavel exige a explicacao (serie, regra, numeros)");
        if (thresholdDescription == null || thresholdDescription.isBlank())
            throw new IllegalArgumentException("alerta exige a descricao do limiar usado");
        if (parameterSnapshot == null || parameterSnapshot.isBlank())
            throw new IllegalArgumentException("alerta exige o snapshot dos parametros (auditoria ADR-0042)");
        Objects.requireNonNull(evidence, "evidencia (trecho da serie) obrigatoria");
        if (evidence.isEmpty())
            throw new IllegalArgumentException("alerta sem trecho da serie nao mostra a serie (aceite)");
        evidence = List.copyOf(evidence);
        Objects.requireNonNull(status, "situacao do alerta obrigatoria");
    }

    /** Verdade se a confianca e reprodutivel: o alerta e auto-contido para auditoria. */
    public boolean isSelfContained() {
        return !evidence.isEmpty() && !explanation.isBlank() && !parameterSnapshot.isBlank();
    }
}
