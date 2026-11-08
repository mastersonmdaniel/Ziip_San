package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curva de recebimento por faixa de atraso (ED-100, GES-10), estimada por
 * estatistica simples sobre a inadimplencia observada: para cada faixa,
 * {@code recoveryRate = recebido / faturado} e {@code lossRate = 1 -
 * recoveryRate} (escala 6, HALF_UP — {@link ForecastMath}). Nenhum modelo
 * alem da razao observada: tecnica estatistica simples, como manda o
 * aceite; aprendizado de maquina so sob o ADR-0042 (ver
 * {@link ForecastTechnique}).
 *
 * <p>A {@code lossRate} desta curva e a taxa da curva de perda consumida
 * pela provisao {@code EXPECTED_LOSS} da ED-32 (ver {@link LossCurve}).
 */
public record CollectionCurve(List<Entry> entries, BigDecimal totalBilled, BigDecimal totalCollected,
                              BigDecimal overallRecoveryRate, BigDecimal overallLossRate) {

    /** Linha da curva: uma faixa, seus totais observados e as duas taxas. */
    public record Entry(DelayBracket bracket, BigDecimal billedAmount, BigDecimal collectedAmount,
                        BigDecimal recoveryRate, BigDecimal lossRate) {
    }

    public CollectionCurve {
        entries = List.copyOf(entries);
    }

    /** Estima a curva a partir das observacoes por faixa (uma por faixa). */
    public static CollectionCurve estimate(List<BracketObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            throw new ForecastValidationException("OBSERVATIONS_REQUIRED",
                    "curva de recebimento exige ao menos uma observacao por faixa");
        }
        Map<String, Entry> byCode = new LinkedHashMap<>();
        List<DelayBracket> seen = new ArrayList<>();
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        for (BracketObservation obs : observations) {
            DelayBracket bracket = obs.bracket();
            if (byCode.containsKey(bracket.code())) {
                throw new ForecastValidationException("BRACKET_DUPLICATED",
                        "faixa repetida na estimativa: " + bracket.code());
            }
            for (DelayBracket other : seen) {
                if (bracket.overlaps(other)) {
                    throw new ForecastValidationException("BRACKETS_OVERLAP",
                            "faixas sobrepostas: " + bracket.code() + " x " + other.code());
                }
            }
            seen.add(bracket);
            BigDecimal recovery = ForecastMath.rate(obs.collectedAmount(), obs.billedAmount());
            BigDecimal loss = ForecastMath.rateValue(BigDecimal.ONE.subtract(recovery));
            byCode.put(bracket.code(), new Entry(bracket, obs.billedAmount(), obs.collectedAmount(), recovery, loss));
            totalBilled = totalBilled.add(obs.billedAmount());
            totalCollected = totalCollected.add(obs.collectedAmount());
        }
        BigDecimal overall = ForecastMath.rate(totalCollected, totalBilled);
        return new CollectionCurve(List.copyOf(byCode.values()), totalBilled, totalCollected,
                overall, ForecastMath.rateValue(BigDecimal.ONE.subtract(overall)));
    }

    /** Linha da faixa pelo codigo; faixa ausente e erro nomeado, nunca taxa presumida. */
    public Entry entryFor(String bracketCode) {
        for (Entry e : entries) {
            if (e.bracket().code().equals(bracketCode)) {
                return e;
            }
        }
        throw new ForecastValidationException("CURVE_BRACKET_MISSING",
                "faixa " + bracketCode + " nao esta na curva de recebimento estimada");
    }

    /** Taxa de recuperacao da faixa ajustada pelo deslocamento do cenario, limitada a [0,1]. */
    public BigDecimal adjustedRecoveryRate(String bracketCode, BigDecimal recoveryShift) {
        if (recoveryShift == null) {
            throw new ForecastValidationException("RECOVERY_SHIFT_REQUIRED",
                    "deslocamento de inadimplencia do cenario e obrigatorio (zero explicito vale)");
        }
        return ForecastMath.clampRate(entryFor(bracketCode).recoveryRate().add(recoveryShift));
    }
}
