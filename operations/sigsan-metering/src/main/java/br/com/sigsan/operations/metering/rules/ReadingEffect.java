package br.com.sigsan.operations.metering.rules;

/**
 * Efeito de uma anormalidade de leitura sobre a <i>leitura</i> faturada (a que vira a "leitura
 * anterior" do mês seguinte).
 *
 * <p>O legado distingue "informada" de "normal"; na cascata (relatório 02, 3.4) os dois mantêm a
 * leitura informada, por isso aqui há um só, {@link #INFORMED}.
 */
public enum ReadingEffect {
    /** Leitura anterior mais a média. */
    PREVIOUS_PLUS_AVERAGE,
    /** Leitura anterior. */
    PREVIOUS,
    /** Leitura anterior mais o consumo (antes do fator). */
    PREVIOUS_PLUS_CONSUMPTION,
    /** Leitura informada. Só existe com leitura. */
    INFORMED;

    boolean validWithoutReading() {
        return this != INFORMED;
    }
}
