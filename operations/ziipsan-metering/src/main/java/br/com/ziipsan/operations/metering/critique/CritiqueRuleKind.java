package br.com.ziipsan.operations.metering.critique;

/**
 * Tipos de regra do motor de critica de leitura (II-2, Onda II do PDCA-01).
 * Cada tipo e um mecanismo; os limiares concretos sao parametros da regra
 * ({@link CritiqueRule}), nunca constantes desta classe.
 */
public enum CritiqueRuleKind {
    /** Leitura actual inferior a anterior, ou consumo medido negativo. */
    NEGATIVE_OR_REGRESSING_READING,
    /** Consumo medido igual a zero. */
    ZERO_CONSUMPTION,
    /** Desvio percentual do consumo face a media do historico acima do limiar. */
    DEVIATION_ABOVE_HISTORY,
    /** Desvio percentual do consumo face a media do historico abaixo do limiar (em modulo). */
    DEVIATION_BELOW_HISTORY,
    /** Consumo fora da faixa absoluta [minimo, maximo] configurada. */
    OUTSIDE_ABSOLUTE_BAND,
    /** Sem leitura informada no ciclo (leitura em falta). */
    MISSING_READING
}
