package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;

/**
 * Dados cadastrais que o consumo de ligação não medida usa. Cada modo usa o seu; o que o modo
 * escolhido exige e não veio é erro ou aviso explícito, nunca um valor presumido calado.
 *
 * @param builtAreaM2        área construída em m² (modo BY_AREA)
 * @param virtualEconomies   economias virtuais para dividir a área (modo BY_AREA)
 * @param utilizationPoints  pontos de utilização (modo BY_UTILIZATION_POINTS); nulo gera aviso e assume 1
 * @param residents          número de moradores (modo BY_RESIDENTS); nulo gera aviso e assume 1
 */
public record NonMeteredFacts(BigDecimal builtAreaM2, Integer virtualEconomies, Integer utilizationPoints,
                              Integer residents) {

    public static final NonMeteredFacts NONE = new NonMeteredFacts(null, null, null, null);
}
