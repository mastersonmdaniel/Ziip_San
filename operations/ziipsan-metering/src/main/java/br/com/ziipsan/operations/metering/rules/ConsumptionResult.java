package br.com.ziipsan.operations.metering.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultado da cascata de consumo de uma unidade em um mês. É o que vira um
 * {@code consumption_records}: o consumo faturado <b>com o tipo e a anormalidade</b> que explicam
 * como se chegou nele, mais os avisos de tudo que foi aproximado.
 *
 * @param billedConsumption        consumo faturado (m³); zero se não fatura
 * @param calculatedConsumption    consumo calculado, antes de a permissão de faturar zerá-lo; é a
 *                                 base da média futura
 * @param billable                 o tipo de consumo permite faturar nesta situação de ligação e o
 *                                 faturamento não está paralisado
 * @param billingPaused            situação especial paralisa o faturamento
 * @param type                     como o consumo foi obtido
 * @param anomaly                  anormalidade de consumo detectada; nula se nenhuma
 * @param billedReadingAnomalyCode código da anormalidade de leitura usada no faturamento (a informada
 *                                 ou a gerada, como hidrômetro parado); nulo se nenhuma
 * @param billedReading            leitura faturada (vira a leitura anterior do mês seguinte); nula sem hidrômetro
 * @param billedReadingDate        data da leitura faturada (diferente da informada quando há ajuste por dias)
 * @param average                  consumo médio usado
 * @param averageMonthsConsidered  meses que compuseram a média
 * @param averageSource            histórico ou consumo mínimo da ligação
 * @param connectionMinimum        consumo mínimo da ligação
 * @param discardedRemainder       m³ descartados pelo ajuste de múltiplas economias (nunca silencioso: há aviso)
 * @param appliedActionMonth       mês de escalonamento (1 a 3) da ação de anormalidade aplicada; nulo se nenhuma
 * @param warnings                 tudo que foi aproximado, ignorado ou precisa de olhar humano
 */
public record ConsumptionResult(
        int billedConsumption,
        int calculatedConsumption,
        boolean billable,
        boolean billingPaused,
        ConsumptionType type,
        ConsumptionAnomalyKind anomaly,
        String billedReadingAnomalyCode,
        Integer billedReading,
        LocalDate billedReadingDate,
        int average,
        int averageMonthsConsidered,
        AverageResult.Source averageSource,
        int connectionMinimum,
        int discardedRemainder,
        Integer appliedActionMonth,
        List<CalculationWarning> warnings) {

    public ConsumptionResult {
        warnings = List.copyOf(warnings);
    }

    public boolean hasWarning(WarningCode code) {
        return warnings.stream().anyMatch(w -> w.code() == code);
    }
}
