package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;

/**
 * Anormalidade de leitura, na parte que a cascata usa: o efeito dela sobre a leitura e sobre o
 * consumo faturados, com e sem leitura atual. Vem do catálogo {@code reading_anomalies} do
 * prestador (sem semente: cada prestador define as suas).
 *
 * <p>Os quatro efeitos são obrigatórios. Cada anormalidade é uma decisão explícita do prestador
 * para os dois casos; não existe efeito "padrão" que esconda uma linha de catálogo incompleta.
 *
 * @param code                           código do prestador (identificação, sem significado para a cascata)
 * @param readingEffectWithoutReading    efeito na leitura faturada quando não há leitura atual
 * @param readingEffectWithReading       efeito na leitura faturada quando há leitura atual
 * @param consumptionEffectWithoutReading efeito no consumo quando não há leitura atual
 * @param consumptionEffectWithReading   efeito no consumo quando há leitura atual
 * @param factorWithoutReading           multiplicador do consumo sem leitura (truncado); nulo = sem fator
 * @param factorWithReading              multiplicador do consumo com leitura (truncado); nulo = sem fator
 * @param forUnitWithoutMeter            anormalidade "imóvel sem hidrômetro" (usada na validação do retorno)
 */
public record ReadingAnomalyRule(
        String code,
        ReadingEffect readingEffectWithoutReading,
        ReadingEffect readingEffectWithReading,
        ConsumptionEffect consumptionEffectWithoutReading,
        ConsumptionEffect consumptionEffectWithReading,
        BigDecimal factorWithoutReading,
        BigDecimal factorWithReading,
        boolean forUnitWithoutMeter) {

    public ReadingAnomalyRule {
        MeteringInputException.require(code != null && !code.isBlank(), MeteringErrorCode.INVALID_INPUT,
                "code da anormalidade de leitura é obrigatório");
        MeteringInputException.notNull(readingEffectWithoutReading, "readingEffectWithoutReading");
        MeteringInputException.notNull(readingEffectWithReading, "readingEffectWithReading");
        MeteringInputException.notNull(consumptionEffectWithoutReading, "consumptionEffectWithoutReading");
        MeteringInputException.notNull(consumptionEffectWithReading, "consumptionEffectWithReading");
        MeteringInputException.require(readingEffectWithoutReading.validWithoutReading(),
                MeteringErrorCode.EFFECT_NOT_APPLICABLE,
                code + ": efeito de leitura " + readingEffectWithoutReading + " não existe sem leitura atual");
        MeteringInputException.require(consumptionEffectWithoutReading.validWithoutReading(),
                MeteringErrorCode.EFFECT_NOT_APPLICABLE,
                code + ": efeito de consumo " + consumptionEffectWithoutReading + " não existe sem leitura atual");
        MeteringInputException.require(factorWithoutReading == null || factorWithoutReading.signum() >= 0,
                MeteringErrorCode.INVALID_INPUT, code + ": fator sem leitura negativo");
        MeteringInputException.require(factorWithReading == null || factorWithReading.signum() >= 0,
                MeteringErrorCode.INVALID_INPUT, code + ": fator com leitura negativo");
    }
}
