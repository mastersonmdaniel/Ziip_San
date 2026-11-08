package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Distribuicao de recebimento por lag (ED-100): a fracao de um mes de
 * faturamento recebida em cada lag, estimada por media ponderada simples
 * das coortes observadas: {@code fraction(lag) = soma(recebido no lag) /
 * soma(faturado das coortes)} (escala 6, HALF_UP). O complementar
 * {@code neverCollectedFraction = 1 - soma(fracoes)} e a fracao nunca
 * recebida na janela observada — a perda agregada implicita.
 *
 * <p>Lag sem observacao tem fracao zero NESTA distribuicao (a fracao e
 * uma estimativa sobre o observado; lag nao observado nao recebeu nada
 * nas coortes que alimentaram a estimativa).
 */
public record LagDistribution(List<Entry> entries, BigDecimal totalFraction, BigDecimal neverCollectedFraction) {

    /** Fracao recebida num lag (0 = mes do faturamento). */
    public record Entry(int lagMonths, BigDecimal fraction) {
    }

    public LagDistribution {
        entries = List.copyOf(entries);
    }

    public static LagDistribution estimate(List<LagObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            throw new ForecastValidationException("LAG_OBSERVATIONS_REQUIRED",
                    "distribuicao de lag exige ao menos uma observacao");
        }
        TreeMap<Integer, BigDecimal[]> byLag = new TreeMap<>();
        for (LagObservation obs : observations) {
            BigDecimal[] sums = byLag.computeIfAbsent(obs.lagMonths(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(obs.cohortBilledAmount());
            sums[1] = sums[1].add(obs.collectedAmount());
        }
        List<Entry> entries = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var e : byLag.entrySet()) {
            BigDecimal fraction = ForecastMath.rate(e.getValue()[1], e.getValue()[0]);
            entries.add(new Entry(e.getKey(), fraction));
            total = total.add(fraction);
        }
        if (total.compareTo(BigDecimal.ONE) > 0) {
            throw new ForecastValidationException("LAG_FRACTIONS_EXCEED_ONE",
                    "soma das fracoes de lag excede 1: " + total
                            + " (observacoes inconsistentes: o mesmo recebimento contado em dois lags?)");
        }
        return new LagDistribution(entries, ForecastMath.rateValue(total),
                ForecastMath.rateValue(BigDecimal.ONE.subtract(total)));
    }

    /** Fracao do lag; lag sem observacao = 0 nesta distribuicao (ver javadoc da classe). */
    public BigDecimal fractionAt(int lagMonths) {
        for (Entry e : entries) {
            if (e.lagMonths() == lagMonths) {
                return e.fraction();
            }
        }
        return BigDecimal.ZERO.setScale(ForecastMath.RATE_SCALE);
    }

    /**
     * Distribuicao ajustada pelo deslocamento de inadimplencia do cenario:
     * o total recuperado vira {@code clamp(total + shift, 0, 1)} e cada
     * fracao e multiplicada pelo fator {@code novoTotal / total} (escala 6),
     * preservando a forma da distribuicao observada. Com total zero nao ha
     * forma a escalar: deslocamento nao zero e recusado.
     */
    public LagDistribution adjusted(BigDecimal recoveryShift) {
        if (recoveryShift == null) {
            throw new ForecastValidationException("RECOVERY_SHIFT_REQUIRED",
                    "deslocamento de inadimplencia do cenario e obrigatorio (zero explicito vale)");
        }
        if (recoveryShift.signum() == 0) {
            return this;
        }
        if (totalFraction.signum() == 0) {
            throw new ForecastValidationException("SHIFT_WITHOUT_BASE",
                    "deslocamento " + recoveryShift + " sobre distribuicao com recuperacao observada zero");
        }
        BigDecimal newTotal = ForecastMath.clampRate(totalFraction.add(recoveryShift));
        BigDecimal factor = ForecastMath.rate(newTotal, totalFraction);
        List<Entry> adjusted = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Entry e : entries) {
            BigDecimal f = ForecastMath.rateValue(e.fraction().multiply(factor));
            adjusted.add(new Entry(e.lagMonths(), f));
            sum = sum.add(f);
        }
        return new LagDistribution(adjusted, ForecastMath.rateValue(sum),
                ForecastMath.rateValue(BigDecimal.ONE.subtract(sum)));
    }
}
