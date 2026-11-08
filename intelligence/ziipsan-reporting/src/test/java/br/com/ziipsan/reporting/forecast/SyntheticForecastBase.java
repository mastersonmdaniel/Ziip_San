package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;
import java.util.List;

/**
 * Base sintetica de inadimplencia CONHECIDA da ED-100 (massa declarada do
 * aceite: "teste com base sintetica de inadimplencia conhecida"). Todos os
 * numeros sao escolhidos para recalculo a mao:
 *
 * <p>Curva por faixa (faturado / recebido / recuperacao / perda):
 * <pre>
 *   ATE_30   10.000,00 / 9.000,00 / 0,900000 / 0,100000
 *   D31_60    5.000,00 / 3.500,00 / 0,700000 / 0,300000
 *   D61_90    2.000,00 /   800,00 / 0,400000 / 0,600000
 *   ACIMA_90  1.000,00 /   200,00 / 0,200000 / 0,800000
 *   TOTAL    18.000,00 / 13.500,00 / 0,750000 / 0,250000
 * </pre>
 *
 * <p>Lags (coorte de 18.000,00; recebido no lag / fracao):
 * <pre>
 *   lag 0: 7.200,00 / 0,400000   lag 1: 3.600,00 / 0,200000
 *   lag 2: 1.800,00 / 0,100000   lag 3:   900,00 / 0,050000
 *   soma das fracoes 0,750000 (= recuperacao total da curva); nunca: 0,250000
 * </pre>
 */
final class SyntheticForecastBase {

    private SyntheticForecastBase() {
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    static List<BracketObservation> bracketObservations() {
        List<DelayBracket> b = DelayBracket.standard();
        return List.of(
                new BracketObservation(b.get(0), bd("10000.00"), bd("9000.00")),
                new BracketObservation(b.get(1), bd("5000.00"), bd("3500.00")),
                new BracketObservation(b.get(2), bd("2000.00"), bd("800.00")),
                new BracketObservation(b.get(3), bd("1000.00"), bd("200.00")));
    }

    static CollectionCurve curve() {
        return CollectionCurve.estimate(bracketObservations());
    }

    static List<LagObservation> lagObservations() {
        return List.of(
                new LagObservation(0, bd("18000.00"), bd("7200.00")),
                new LagObservation(1, bd("18000.00"), bd("3600.00")),
                new LagObservation(2, bd("18000.00"), bd("1800.00")),
                new LagObservation(3, bd("18000.00"), bd("900.00")));
    }

    static LagDistribution lagDistribution() {
        return LagDistribution.estimate(lagObservations());
    }
}
