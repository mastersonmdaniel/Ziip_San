package br.com.ziipsan.reporting.forecast;

/**
 * Recusa nomeada da projecao (ED-100). Toda rejeicao carrega um codigo
 * estavel (ex.: SCENARIO_FIELD_REQUIRED) para o chamador distinguir a causa
 * sem parsear texto. Premissa ausente e erro explicito: nada e presumido,
 * nada e aceito "com ajuste" silencioso.
 */
public class ForecastValidationException extends IllegalArgumentException {

    private final String code;

    public ForecastValidationException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
