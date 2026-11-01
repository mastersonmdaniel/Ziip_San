package br.com.ziipsan.operations.metering.rules;

import java.time.YearMonth;

/**
 * Um mês do histórico de consumo da unidade.
 *
 * @param reference                mês do registro
 * @param averageBasisConsumption  consumo-base para a média (o "calculado", não o faturado final:
 *                                 um mês não faturado por situação especial ainda tem base)
 * @param type                     tipo de consumo gravado
 * @param anomaly                  anormalidade gravada; nula se não houve
 */
public record HistoryEntry(
        YearMonth reference,
        int averageBasisConsumption,
        ConsumptionType type,
        ConsumptionAnomalyKind anomaly) {

    public HistoryEntry {
        MeteringInputException.notNull(reference, "reference");
        MeteringInputException.notNull(type, "type");
        MeteringInputException.require(averageBasisConsumption >= 0, MeteringErrorCode.INVALID_INPUT,
                "consumo do histórico negativo em " + reference);
    }
}
