package br.com.ziipsan.reporting.indicator;

/**
 * Recusa nomeada do registro de indicadores (P3.41). Toda rejeicao carrega um
 * codigo estavel no inicio da mensagem (ex.: VARIABLE_WITHOUT_SOURCE) para o
 * chamador e o relatorio de recusas distinguirem a causa sem parsear texto.
 * Nada e recusado em silencio e nada e aceito "com ajuste": ou o registro
 * esta conforme a regra, ou esta excecao nomeia o motivo.
 */
public class IndicatorValidationException extends IllegalArgumentException {

    private final String code;

    public IndicatorValidationException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
