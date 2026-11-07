package br.com.ziipsan.operations.metering.rules;

/**
 * Como o consumo é faturado sob uma ocorrência de leitura (JOR-10): a leitura do
 * {@link ConsumptionEffect} da ocorrência na linguagem do catálogo de ocorrências — por média,
 * por mínimo, pelo medido ou por estimativa de ligação não medida.
 *
 * <p>O mapeamento é 1:1 com {@link ConsumptionEffect} de propósito: a cascata
 * ({@link ConsumptionCalculator}) continua decidindo pelo efeito, e o catálogo de ocorrências
 * (P2.5) expõe a mesma decisão para a conta, para a OS e para a tela, sem uma segunda tabela de
 * conversão que pudesse divergir da cascata.
 *
 * <p>"Sem faturar" não é um efeito de anormalidade de leitura neste módulo: quem zera o faturado
 * é a permissão de faturar da situação de ligação ({@link ConnectionCondition}) ou a paralisação
 * por situação especial ({@link SpecialSituation}), e o resultado da cascata já o expõe em
 * {@code ConsumptionResult.billable() == false} com o consumo calculado preservado. Uma
 * ocorrência, sozinha, nunca é representada aqui como "sem faturar" — ver notas da frente F4.
 */
public enum OccurrenceBillingMode {
    /** Fatura a média ({@link ConsumptionEffect#AVERAGE}). */
    BY_AVERAGE,
    /**
     * Fatura pelo mínimo ({@link ConsumptionEffect#MINIMUM}). A assimetria é a da cascata:
     * sem leitura, o consumo mínimo da ligação; com leitura, zero (tipo "sem consumo").
     */
    BY_MINIMUM,
    /** Mantém o consumo medido ({@link ConsumptionEffect#NORMAL}); só existe com leitura. */
    BY_MEASURED,
    /** O maior entre a média e o medido ({@link ConsumptionEffect#MAX_OF_AVERAGE_AND_MEASURED}); só com leitura. */
    BY_MAX_OF_AVERAGE_AND_MEASURED,
    /** O menor entre a média e o medido ({@link ConsumptionEffect#MIN_OF_AVERAGE_AND_MEASURED}); só com leitura. */
    BY_MIN_OF_AVERAGE_AND_MEASURED,
    /** Consumo de ligação não medida ({@link ConsumptionEffect#NOT_METERED}). */
    BY_NOT_METERED_ESTIMATE;

    /** O modo correspondente ao efeito de consumo da ocorrência. */
    public static OccurrenceBillingMode from(ConsumptionEffect effect) {
        MeteringInputException.notNull(effect, "effect");
        return switch (effect) {
            case AVERAGE -> BY_AVERAGE;
            case MINIMUM -> BY_MINIMUM;
            case NORMAL -> BY_MEASURED;
            case MAX_OF_AVERAGE_AND_MEASURED -> BY_MAX_OF_AVERAGE_AND_MEASURED;
            case MIN_OF_AVERAGE_AND_MEASURED -> BY_MIN_OF_AVERAGE_AND_MEASURED;
            case NOT_METERED -> BY_NOT_METERED_ESTIMATE;
        };
    }

    /** Modos que fazem sentido sem leitura atual (espelha {@link ConsumptionEffect#validWithoutReading()}). */
    public boolean validWithoutReading() {
        return this == BY_AVERAGE || this == BY_MINIMUM || this == BY_NOT_METERED_ESTIMATE;
    }
}
