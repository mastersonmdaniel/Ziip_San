package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;

/**
 * Patamares de estouro, alto e baixo consumo de uma categoria (relatório 02, 3.6). São política do
 * prestador, por categoria; não há valor padrão. Campo nulo = "não configurado", e a cascata avisa
 * (nunca presume um valor).
 *
 * @param burstConsumption    consumo por economia acima do qual há estouro (somado por economia)
 * @param burstTimesAverage   vezes a média acima das quais há estouro (usado na categoria principal)
 * @param burstMaximum        consumo máximo por economia para manter um estouro reincidente (somado por economia)
 * @param highConsumption     consumo por economia acima do qual há alto consumo
 * @param highTimesAverage    vezes a média acima das quais há alto consumo (categoria principal)
 * @param lowAverageFloor     média por economia acima da qual o baixo consumo é avaliado
 * @param lowPercentOfAverage percentual da média abaixo do qual há baixo consumo (categoria principal)
 */
public record CategoryThresholds(
        Integer burstConsumption,
        BigDecimal burstTimesAverage,
        Integer burstMaximum,
        Integer highConsumption,
        BigDecimal highTimesAverage,
        Integer lowAverageFloor,
        BigDecimal lowPercentOfAverage) {

    public CategoryThresholds {
        nonNegative(burstConsumption, "burstConsumption");
        nonNegative(burstMaximum, "burstMaximum");
        nonNegative(highConsumption, "highConsumption");
        nonNegative(lowAverageFloor, "lowAverageFloor");
        nonNegative(burstTimesAverage, "burstTimesAverage");
        nonNegative(highTimesAverage, "highTimesAverage");
        nonNegative(lowPercentOfAverage, "lowPercentOfAverage");
    }

    private static void nonNegative(Integer v, String field) {
        MeteringInputException.require(v == null || v >= 0, MeteringErrorCode.INVALID_INPUT, field + " negativo");
    }

    private static void nonNegative(BigDecimal v, String field) {
        MeteringInputException.require(v == null || v.signum() >= 0, MeteringErrorCode.INVALID_INPUT,
                field + " negativo");
    }
}
