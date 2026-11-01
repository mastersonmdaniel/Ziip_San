package br.com.ziipsan.operations.metering.rules;

/**
 * Aviso do cálculo: o resultado é válido, mas alguma coisa foi aproximada, ignorada ou precisa de
 * olhar humano. É a forma de a cascata nunca descartar nada em silêncio.
 */
public record CalculationWarning(WarningCode code, String message) {

    public CalculationWarning {
        MeteringInputException.notNull(code, "code");
        MeteringInputException.notNull(message, "message");
    }

    @Override
    public String toString() {
        return code + ": " + message;
    }
}
