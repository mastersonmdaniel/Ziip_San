package br.com.sigsan.operations.metering.rules;

/**
 * Condição que a cascata detectou no consumo. É o que a cascata produz; o catálogo
 * {@code consumption_anomalies} de cada prestador dá a descrição, a mensagem da conta e se a
 * anormalidade entra na média, e se liga a esta lista pelo campo {@code kind}.
 *
 * <p>Não há aqui número de código: o legado identifica anormalidades por id numérico espalhado
 * pelo código (armadilha 8 do relatório 02). O identificador é o nome simbólico.
 */
public enum ConsumptionAnomalyKind {
    /** Consumo informado no lugar do medido. */
    CONSUMPTION_INFORMED,
    /** Consumo abaixo do patamar de baixo consumo. */
    LOW_CONSUMPTION,
    /** Consumo acima do patamar de estouro. */
    BURST,
    /** Consumo acima do patamar de alto consumo. */
    HIGH_CONSUMPTION,
    /** Leitura atual menor que a anterior, sem leitura anterior realizada (projetada). */
    READING_BELOW_PROJECTED,
    /** Leitura atual menor que a anterior, com leitura anterior realizada. */
    READING_BELOW_PREVIOUS,
    /** Hidrômetro substituído no período, com leitura informada. */
    METER_REPLACED_INFORMED,
    /** Leitura não informada e sem anormalidade de leitura. */
    READING_NOT_INFORMED,
    /** Estouro na primeira ocorrência: cobra a média. */
    BURST_CHARGED_AT_AVERAGE,
    /** Consumo mínimo fixado na ligação aplicado. */
    MINIMUM_FIXED,
    /** Leitura fora da faixa esperada. */
    OUT_OF_RANGE,
    /** Virada do mostrador do hidrômetro. */
    METER_ROLLOVER,
    /** Anormalidade de leitura aplicada sem leitura atual. */
    READING_ANOMALY;

    /**
     * Estouro na primeira ocorrência (cobra a média) e estouro mantido são a mesma família para
     * decidir reincidência. Sem isso, depois do primeiro mês cobrado pela média o estouro seguinte
     * nunca seria visto como reincidente.
     */
    boolean sameFamilyAs(ConsumptionAnomalyKind other) {
        if (other == null) {
            return false;
        }
        boolean thisBurst = this == BURST || this == BURST_CHARGED_AT_AVERAGE;
        boolean otherBurst = other == BURST || other == BURST_CHARGED_AT_AVERAGE;
        return (thisBurst && otherBurst) || this == other;
    }
}
