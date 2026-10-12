package br.com.sigsan.operations.metering.rules;

/**
 * Faixa de leitura esperada para o mês.
 *
 * <p>Quando a faixa atravessa a virada do mostrador, só uma ponta passa de {@code 10^dígitos - 1}
 * e a faixa vira "do {@code low} até o fim do mostrador, e do zero até o {@code high}"
 * ({@link #wraps()}). O legado testa "menor que o início OU maior que o fim" e, nesse caso, acusa
 * toda leitura como fora da faixa; aqui {@link #contains} trata a virada.
 *
 * @param low    leitura mínima esperada (0 a 10^dígitos - 1)
 * @param high   leitura máxima esperada (0 a 10^dígitos - 1)
 * @param digits dígitos do mostrador do hidrômetro
 */
public record ExpectedReadingRange(int low, int high, int digits) {

    /** A faixa atravessa a virada do mostrador. */
    public boolean wraps() {
        return low > high;
    }

    public boolean contains(int reading) {
        return wraps() ? (reading >= low || reading <= high) : (reading >= low && reading <= high);
    }

    public boolean isOutOfRange(int reading) {
        return !contains(reading);
    }
}
