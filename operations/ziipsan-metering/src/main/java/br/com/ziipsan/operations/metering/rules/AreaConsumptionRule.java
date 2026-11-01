package br.com.ziipsan.operations.metering.rules;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Linha da tabela de consumo mínimo por área (ligação não medida, modo
 * {@link NonMeteredMode#BY_AREA}). Vem do prestador; vale a partir de {@code validFrom}.
 *
 * @param validFrom   primeira referência em que a linha vale
 * @param maxArea     área virtual máxima (inclusive) atendida pela linha, em m²
 * @param consumption consumo em m³ por economia
 */
public record AreaConsumptionRule(YearMonth validFrom, BigDecimal maxArea, int consumption) {

    public AreaConsumptionRule {
        MeteringInputException.notNull(validFrom, "validFrom");
        MeteringInputException.require(maxArea != null && maxArea.signum() > 0, MeteringErrorCode.INVALID_INPUT,
                "maxArea deve ser positivo");
        MeteringInputException.require(consumption >= 0, MeteringErrorCode.INVALID_INPUT, "consumption negativo");
    }
}
