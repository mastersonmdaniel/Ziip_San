package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * O motor de critica (II-2, E2): avalia as regras configuradas sobre a
 * leitura + historico. Funcao pura e deterministica -- sem relogio, sem
 * aleatoriedade, sem estado. Fronteiras ESTRITAS (ver {@link CritiqueRule}):
 * igualdade ao limiar nao dispara.
 *
 * <p>Desvio: {@code |medido - media| * 100 / media}, com a media aritmetica
 * exacta do historico (BigDecimal, escala 10, HALF_UP na divisao). Historico
 * vazio ou media zero: as regras de desvio nao disparam (sem base de
 * comparacao nao ha desvio -- declarado, nao aproximado).
 */
public final class CritiqueEngine {

    public CritiqueResult evaluate(ReadingSubmission submission, CritiqueConfig config) {
        List<RuleFinding> findings = new ArrayList<>();
        for (CritiqueRule rule : config.rules()) {
            if (!rule.enabled()) {
                continue;
            }
            RuleFinding finding = apply(rule, submission);
            if (finding != null) {
                findings.add(finding);
            }
        }
        return new CritiqueResult(submission.unitId(), submission.reference(), findings);
    }

    private RuleFinding apply(CritiqueRule rule, ReadingSubmission s) {
        return switch (rule.kind()) {
            case MISSING_READING -> s.currentReading() == null
                    ? new RuleFinding(rule.code(), rule.kind(), null, null, "sem leitura informada no ciclo")
                    : null;
            case NEGATIVE_OR_REGRESSING_READING -> negativeFinding(rule, s);
            case ZERO_CONSUMPTION -> s.measuredConsumption() != null && s.measuredConsumption() == 0
                    ? new RuleFinding(rule.code(), rule.kind(), BigDecimal.ZERO, null, "consumo medido igual a zero")
                    : null;
            case DEVIATION_ABOVE_HISTORY, DEVIATION_BELOW_HISTORY -> deviationFinding(rule, s);
            case OUTSIDE_ABSOLUTE_BAND -> bandFinding(rule, s);
        };
    }

    private RuleFinding negativeFinding(CritiqueRule rule, ReadingSubmission s) {
        if (s.measuredConsumption() != null && s.measuredConsumption() < 0) {
            return new RuleFinding(rule.code(), rule.kind(),
                    BigDecimal.valueOf(s.measuredConsumption()), null, "consumo medido negativo");
        }
        if (s.currentReading() != null && s.previousReading() != null
                && s.currentReading() < s.previousReading()) {
            return new RuleFinding(rule.code(), rule.kind(),
                    BigDecimal.valueOf(s.currentReading()), BigDecimal.valueOf(s.previousReading()),
                    "leitura actual inferior a anterior");
        }
        return null;
    }

    private RuleFinding deviationFinding(CritiqueRule rule, ReadingSubmission s) {
        if (s.measuredConsumption() == null || s.history().isEmpty()) {
            return null;
        }
        BigDecimal mean = historicalMean(s.history());
        if (mean.signum() == 0) {
            return null;
        }
        BigDecimal deviation = BigDecimal.valueOf(s.measuredConsumption()).subtract(mean)
                .multiply(BigDecimal.valueOf(100)).divide(mean, 10, RoundingMode.HALF_UP);
        boolean above = rule.kind() == CritiqueRuleKind.DEVIATION_ABOVE_HISTORY;
        boolean fires = above
                ? deviation.compareTo(rule.deviationPercent()) > 0
                : deviation.signum() < 0 && deviation.negate().compareTo(rule.deviationPercent()) > 0;
        if (!fires) {
            return null;
        }
        return new RuleFinding(rule.code(), rule.kind(), deviation, rule.deviationPercent(),
                (above ? "desvio acima do historico: " : "desvio abaixo do historico: ") + deviation + "%");
    }

    private RuleFinding bandFinding(CritiqueRule rule, ReadingSubmission s) {
        if (s.measuredConsumption() == null) {
            return null;
        }
        int measured = s.measuredConsumption();
        if (measured < rule.bandMin()) {
            return new RuleFinding(rule.code(), rule.kind(), BigDecimal.valueOf(measured),
                    BigDecimal.valueOf(rule.bandMin()), "consumo abaixo do minimo da faixa");
        }
        if (measured > rule.bandMax()) {
            return new RuleFinding(rule.code(), rule.kind(), BigDecimal.valueOf(measured),
                    BigDecimal.valueOf(rule.bandMax()), "consumo acima do maximo da faixa");
        }
        return null;
    }

    /** Media aritmetica exacta do historico (soma / n, escala 10, HALF_UP). */
    public static BigDecimal historicalMean(List<Integer> history) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("media de historico vazio nao existe");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int value : history) {
            sum = sum.add(BigDecimal.valueOf(value));
        }
        return sum.divide(BigDecimal.valueOf(history.size()), 10, RoundingMode.HALF_UP);
    }
}
