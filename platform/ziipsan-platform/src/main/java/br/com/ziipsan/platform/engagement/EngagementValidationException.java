package br.com.ziipsan.platform.engagement;

/**
 * Recusa nomeada da ED-101 (padrao das recusas nomeadas do projeto, como a
 * {@code ForecastValidationException} da ED-100): toda guarda que barra uma
 * operacao de engajamento carrega um codigo estavel, para a integracao
 * traduzir em erro de API sem depender do texto da mensagem.
 */
public class EngagementValidationException extends RuntimeException {

    private final String code;

    public EngagementValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
