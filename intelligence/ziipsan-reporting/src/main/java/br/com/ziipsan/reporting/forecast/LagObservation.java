package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;

/**
 * Observacao por defasagem (lag) de recebimento (ED-100): de uma coorte
 * (mes de faturamento), quanto foi recebido {@code lagMonths} meses depois
 * do faturamento. Quem chama agrega os eventos da ED-32 por coorte e lag;
 * varias coortes podem alimentar o mesmo lag (a distribuicao pondera
 * pelos valores — media ponderada simples).
 */
public record LagObservation(int lagMonths, BigDecimal cohortBilledAmount, BigDecimal collectedAmount) {

    public LagObservation {
        if (lagMonths < 0) {
            throw new ForecastValidationException("LAG_INVALID", "lag negativo: " + lagMonths);
        }
        ForecastMath.requireMoney("faturado da coorte (lag " + lagMonths + ")", cohortBilledAmount);
        ForecastMath.requireMoney("recebido no lag " + lagMonths, collectedAmount);
        if (cohortBilledAmount.signum() == 0) {
            throw new ForecastValidationException("LAG_BILLED_ZERO",
                    "lag " + lagMonths + " com faturado da coorte zero: fracao indefinida");
        }
        if (collectedAmount.compareTo(cohortBilledAmount) > 0) {
            throw new ForecastValidationException("COLLECTED_EXCEEDS_BILLED",
                    "lag " + lagMonths + ": recebido " + collectedAmount
                            + " maior que o faturado da coorte " + cohortBilledAmount);
        }
    }
}
