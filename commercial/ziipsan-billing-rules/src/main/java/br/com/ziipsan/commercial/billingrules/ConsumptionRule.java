package br.com.ziipsan.commercial.billingrules;

/**
 * Consumo a partir das leituras do mostrador (ED-19), regra pura: diferenca
 * simples quando a leitura atual nao retrocede; virada do mostrador
 * (rolagem) quando retrocede — o consumo e o que falta para completar
 * 10^digitos mais a leitura atual. Retrocesso SEM compatibilidade com a
 * virada nao existe nesta regra: todo retrocesso e tratado como virada,
 * exatamente como a comparacao de historico da ED-18 admite a virada; quem
 * critica plausibilidade (consumo absurdo, media) e a critica de leitura,
 * nao esta subtracao.
 */
public final class ConsumptionRule {

    private ConsumptionRule() {
    }

    /**
     * @param previousReading leitura anterior (a do pacote da rota)
     * @param currentReading  leitura digitada/conferida em campo
     * @param meterDigits     digitos do mostrador (capacidade = 10^digitos)
     */
    public static int consumption(int previousReading, int currentReading, int meterDigits) {
        if (meterDigits < 1 || meterDigits > 9) {
            throw new IllegalArgumentException("Digitos do mostrador fora de [1, 9]: " + meterDigits);
        }
        long capacity = 1L;
        for (int i = 0; i < meterDigits; i++) {
            capacity *= 10L;
        }
        if (previousReading < 0 || previousReading >= capacity) {
            throw new IllegalArgumentException(
                    "Leitura anterior fora do mostrador de " + meterDigits + " digitos: " + previousReading);
        }
        if (currentReading < 0 || currentReading >= capacity) {
            throw new IllegalArgumentException(
                    "Leitura atual fora do mostrador de " + meterDigits + " digitos: " + currentReading);
        }
        if (currentReading >= previousReading) {
            return currentReading - previousReading;
        }
        // Virada do mostrador: completa o ciclo e soma a leitura nova.
        return (int) ((capacity - previousReading) + currentReading);
    }
}
