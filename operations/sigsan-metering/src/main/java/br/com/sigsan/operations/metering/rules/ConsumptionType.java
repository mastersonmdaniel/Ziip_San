package br.com.sigsan.operations.metering.rules;

/**
 * Como o consumo faturado foi obtido. Gravado em cada registro de consumo: é o que permite
 * responder, meses depois, "por que cobrou X".
 *
 * <p>É um enum fechado porque a cascata decide por ele (por exemplo, quais tipos entram na média
 * e quais permitem faturar). O que é política do prestador não está aqui: quais tipos entram na
 * média ({@link AveragePolicy}) e quais permitem faturar ({@link ConnectionCondition}).
 */
public enum ConsumptionType {
    /** Diferença de leituras com a leitura anterior realizada ou confirmada. */
    REAL,
    /** Consumo proporcional aos dias (ajuste por dias, ou leitura ausente fora da tolerância). */
    ADJUSTED_AVERAGE,
    /** Consumo igual à média do hidrômetro. */
    METER_AVERAGE,
    /** Consumo informado pelo prestador no lugar do medido. */
    INFORMED,
    /** Ligação sem hidrômetro: consumo por tarifa, área, pontos ou moradores. */
    NOT_METERED,
    /** Diferença de leituras, mas a leitura anterior não era realizada nem confirmada. */
    ESTIMATED,
    /** Consumo mínimo fixado na ligação. */
    MINIMUM_FIXED,
    /** Sem consumo (efeito "mínimo" de anormalidade). */
    NO_CONSUMPTION,
    /**
     * Média do imóvel. Reservado: o relatório cita o tipo no esgoto, mas nenhuma regra da cascata
     * o produz. Fica no enum para o catálogo de tipos que entram na média.
     */
    UNIT_AVERAGE,
    /** Consumo fixado por situação especial de faturamento. */
    SPECIAL_SITUATION_FIXED;

    /** Tipos de média: o esgoto não aplica o percentual de coleta sobre eles. */
    boolean isAverage() {
        return this == METER_AVERAGE || this == UNIT_AVERAGE;
    }
}
