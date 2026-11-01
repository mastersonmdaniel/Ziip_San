package br.com.ziipsan.operations.metering.rules;

import java.time.YearMonth;

/**
 * Linha da tabela de consumo mínimo por parâmetro (pontos de utilização ou número de moradores).
 *
 * @param validFrom    primeira referência em que a linha vale
 * @param maxParameter maior valor do parâmetro (inclusive) atendido pela linha
 * @param consumption  consumo em m³ por economia
 */
public record ParameterConsumptionRule(YearMonth validFrom, int maxParameter, int consumption) {

    public ParameterConsumptionRule {
        MeteringInputException.notNull(validFrom, "validFrom");
        MeteringInputException.require(maxParameter >= 0, MeteringErrorCode.INVALID_INPUT, "maxParameter negativo");
        MeteringInputException.require(consumption >= 0, MeteringErrorCode.INVALID_INPUT, "consumption negativo");
    }
}
