package br.com.sigsan.operations.metering.rules;

import java.util.List;

/**
 * Consumo médio e de onde ele veio.
 *
 * @param average           média em m³ (inteira)
 * @param monthsConsidered  quantos meses entraram
 * @param source            histórico ou, na falta dele, o consumo mínimo da ligação
 * @param warnings          avisos (ex.: média substituída pelo mínimo por falta de histórico)
 */
public record AverageResult(int average, int monthsConsidered, Source source, List<CalculationWarning> warnings) {

    public enum Source { HISTORY, CONNECTION_MINIMUM }

    public AverageResult {
        warnings = List.copyOf(warnings);
    }
}
