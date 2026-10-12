package br.com.sigsan.operations.metering.rules;

/**
 * Efeito de uma anormalidade (de leitura ou de consumo) sobre o consumo faturado.
 *
 * <p>O legado tem dois códigos para "média" ("não ocorre" e "média") e os trata do mesmo modo na
 * cascata; aqui há um só, {@link #AVERAGE}.
 */
public enum ConsumptionEffect {
    /** Fatura a média. */
    AVERAGE,
    /**
     * Mínimo. Sem leitura: o consumo mínimo da ligação, tipo "sem consumo". Com leitura: zero,
     * tipo "sem consumo". A assimetria é do relatório 02 (3.4) e foi preservada; confirmar com o
     * prestador antes de usar em produção.
     */
    MINIMUM,
    /** Mantém o consumo calculado. Só existe com leitura. */
    NORMAL,
    /** O maior entre a média e o medido. Só existe com leitura. */
    MAX_OF_AVERAGE_AND_MEASURED,
    /** O menor entre a média e o medido. Só existe com leitura. */
    MIN_OF_AVERAGE_AND_MEASURED,
    /** Consumo de ligação não medida (tarifa, área, pontos ou moradores). */
    NOT_METERED;

    /** Efeitos que fazem sentido sem leitura atual (não há consumo medido para comparar). */
    boolean validWithoutReading() {
        return this == AVERAGE || this == MINIMUM || this == NOT_METERED;
    }
}
