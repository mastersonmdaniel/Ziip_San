package br.com.ziipsan.operations.metering.rules;

import java.math.BigDecimal;

/**
 * Quando uma leitura atual menor que a anterior é uma virada do mostrador e não um erro.
 *
 * <p>O consumo de virada é {@code (atual + 10^dígitos) - anterior}. Ele é plausível se for
 * positivo e, ao mesmo tempo, não passar de {@code maxPlausibleConsumption} <b>ou</b> de
 * {@code averageMultiple} vezes a média. Os dois limites são política do prestador: o legado os
 * tem fixos no código (200 m³ e 2 vezes a média), e aqui não há valor padrão.
 *
 * @param maxPlausibleConsumption consumo de virada aceito sem olhar a média (m³)
 * @param averageMultiple         múltiplo da média aceito (ex.: dobro)
 */
public record RolloverPolicy(int maxPlausibleConsumption, BigDecimal averageMultiple) {

    public RolloverPolicy {
        MeteringInputException.require(maxPlausibleConsumption >= 0, MeteringErrorCode.INVALID_INPUT,
                "maxPlausibleConsumption negativo");
        MeteringInputException.require(averageMultiple != null && averageMultiple.signum() >= 0,
                MeteringErrorCode.INVALID_INPUT, "averageMultiple é obrigatório e não negativo");
    }
}
