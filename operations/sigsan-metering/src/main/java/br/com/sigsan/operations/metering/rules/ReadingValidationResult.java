package br.com.sigsan.operations.metering.rules;

import java.util.List;

/**
 * Resultado da validação de uma linha de retorno. Traz <b>todas</b> as violações, não só a
 * primeira: o relatório de erros mostra tudo que há para corrigir de uma vez.
 */
public record ReadingValidationResult(List<ReadingViolation> violations, List<CalculationWarning> warnings) {

    public ReadingValidationResult {
        violations = List.copyOf(violations);
        warnings = List.copyOf(warnings);
    }

    public boolean accepted() {
        return violations.isEmpty();
    }
}
