package br.com.ziipsan.operations.metering.rules;

/** Como se calcula o consumo de uma ligação sem hidrômetro. Escolha do prestador (por tenant). */
public enum NonMeteredMode {
    /** Consumo mínimo da ligação (soma, por categoria, do mínimo da tarifa vezes as economias). */
    BY_TARIFF,
    /** Tabela de consumo por faixa de área construída por economia virtual. */
    BY_AREA,
    /** Tabela de consumo por faixa de pontos de utilização. */
    BY_UTILIZATION_POINTS,
    /** Tabela de consumo por faixa de número de moradores. */
    BY_RESIDENTS
}
