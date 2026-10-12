package br.com.sigsan.operations.metering.rules;

import java.util.List;

/**
 * Política do prestador (por tenant) para a cascata de consumo. Nada aqui tem valor padrão: o
 * código de produção não carrega número de política, e o que faltar impede a construção.
 *
 * @param average                     média: meses, retroação, tipos e anormalidades que entram nela
 * @param expectedRange               faixas da leitura esperada
 * @param rollover                    limites de plausibilidade da virada do mostrador
 * @param noReadingToleranceDays      tolerância, em dias, entre os dias reais e o ciclo para faturar a
 *                                    média sem proporcionalizar quando não há leitura
 * @param dayAdjustmentToleranceDays  diferença máxima, em dias, entre o ciclo e os dias reais antes de
 *                                    o ajuste mensal por dias agir
 * @param stoppedMeterReadingFloor    a leitura precisa ser maior que este valor para o hidrômetro
 *                                    parado ser acusado (o legado usa 1: ignora hidrômetro recém-zerado)
 * @param stoppedMeterRule            anormalidade de leitura gerada por hidrômetro parado; nula = não
 *                                    configurada (a cascata avisa)
 * @param stoppedMeterNoConsumptionRule anormalidade gerada por hidrômetro parado quando há poço ou
 *                                    esgoto com mínimo fixado; nula = não configurada
 * @param nonMeteredMode              como calcular a ligação sem hidrômetro
 * @param actions                     ações por anormalidade de consumo e categoria (pode ser vazia)
 */
public record ConsumptionPolicy(
        AveragePolicy average,
        ExpectedRangePolicy expectedRange,
        RolloverPolicy rollover,
        int noReadingToleranceDays,
        int dayAdjustmentToleranceDays,
        int stoppedMeterReadingFloor,
        ReadingAnomalyRule stoppedMeterRule,
        ReadingAnomalyRule stoppedMeterNoConsumptionRule,
        NonMeteredMode nonMeteredMode,
        List<ConsumptionAnomalyAction> actions) {

    public ConsumptionPolicy {
        MeteringInputException.notNull(average, "average");
        MeteringInputException.notNull(expectedRange, "expectedRange");
        MeteringInputException.notNull(rollover, "rollover");
        MeteringInputException.require(noReadingToleranceDays >= 0, MeteringErrorCode.INVALID_INPUT,
                "noReadingToleranceDays negativo");
        MeteringInputException.require(dayAdjustmentToleranceDays >= 0, MeteringErrorCode.INVALID_INPUT,
                "dayAdjustmentToleranceDays negativo");
        MeteringInputException.require(stoppedMeterReadingFloor >= 0, MeteringErrorCode.INVALID_INPUT,
                "stoppedMeterReadingFloor negativo");
        MeteringInputException.notNull(nonMeteredMode, "nonMeteredMode");
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    ConsumptionAnomalyAction actionFor(ConsumptionAnomalyKind kind, String categoryCode) {
        return actions.stream()
                .filter(a -> a.kind() == kind && a.categoryCode().equals(categoryCode))
                .findFirst()
                .orElse(null);
    }
}
