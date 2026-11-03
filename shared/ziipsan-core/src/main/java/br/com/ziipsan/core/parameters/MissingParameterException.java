package br.com.ziipsan.core.parameters;

/**
 * Operação que depende de um parâmetro do tenant sem valor aceito e vigente.
 *
 * <p>Princípio 9 do PARAMETROS-DO-TENANT e ADR-0002, item 3: ausência é erro explícito,
 * nunca valor assumido em silêncio. A mensagem nomeia a chave e a data de referência,
 * para que o operador saiba exatamente o que falta aceitar.
 */
public class MissingParameterException extends RuntimeException {

    private final String definitionKey;

    public MissingParameterException(String definitionKey, String detail) {
        super("Parâmetro '" + definitionKey + "' sem valor aceito e vigente: " + detail);
        this.definitionKey = definitionKey;
    }

    public String definitionKey() {
        return definitionKey;
    }
}
