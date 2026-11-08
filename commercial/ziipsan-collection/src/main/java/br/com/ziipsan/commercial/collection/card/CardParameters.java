package br.com.ziipsan.commercial.collection.card;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Leitura tipada dos parametros do tenant consumidos pelo pagamento com
 * cartao (ED-28), no padrao de {@code CollectionParameters} (P3.1) e
 * {@code DunningParameters} (P4.1): o chamador resolve os valores
 * vigentes e aceitos e entrega o JSON de cada chave; esta classe
 * interpreta — sempre pela classe {@link Json} do core — e valida.
 *
 * <p>Principio (precedente de {@code pay.withholding_enabled} da P3.1):
 * parametro ausente e erro explicito ({@link MissingParameterException}),
 * nunca valor assumido. Em especial {@code card.enabled} NAO tem
 * padrao: presumir "desligado" esconderia um parametro nao publicado e
 * presumir "ligado" ativaria cobranca por cartao sem o contrato do
 * tenant com a credenciadora (a credenciadora e contratada PELO TENANT
 * — 18e-E14/D3; o produto nao tem credenciadora propria).
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   card.enabled              BOOLEAN  (sem padrao: ausencia e erro explicito)
 *   card.acquirer_code        TEXT     codigo da credenciadora contratada pelo tenant
 *                                      no cadastro de credenciadoras da integracao;
 *                                      obrigatorio quando card.enabled = true
 *   card.checkout_mode        TEXT     PAYMENT_LINK | HOSTED_CHECKOUT
 *   card.statement_tolerance  DECIMAL  tolerancia (R$) da conciliacao pelo extrato
 * </pre>
 *
 * <p>Credenciais da credenciadora (chaves de API, segredo do webhook)
 * NAO sao parametros nem passam por esta classe: vivem no cofre de
 * credenciais e so o adaptador da credenciadora as le (marcador de
 * integracao no relatorio da ED-28).
 */
public final class CardParameters {

    public static final String ENABLED = "card.enabled";
    public static final String ACQUIRER_CODE = "card.acquirer_code";
    public static final String CHECKOUT_MODE = "card.checkout_mode";
    public static final String STATEMENT_TOLERANCE = "card.statement_tolerance";

    private final Map<String, String> jsonByKey;

    private CardParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static CardParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new CardParameters(Map.copyOf(jsonByKey));
    }

    /** Cartao ligado para o tenant? Ausencia e erro explicito (ver javadoc da classe). */
    public boolean enabled() {
        JsonNode node = required(ENABLED);
        if (!node.isBoolean()) {
            throw new IllegalStateException(ENABLED + ": esperado booleano, veio " + node.getNodeType());
        }
        return node.booleanValue();
    }

    /**
     * Codigo da credenciadora contratada pelo tenant. So e exigido com o
     * recurso ligado; desligado, a ausencia e legitima e devolve null —
     * mas codigo em branco com o recurso ligado e erro explicito: sem
     * credenciadora identificada nao ha para onde mandar o pagador.
     */
    public String acquirerCode() {
        if (!enabled()) {
            return null;
        }
        String json = jsonByKey.get(ACQUIRER_CODE);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(ACQUIRER_CODE,
                    "card.enabled esta ligado, mas nenhuma credenciadora foi configurada");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull() || !node.isTextual() || node.textValue().isBlank()) {
            throw new MissingParameterException(ACQUIRER_CODE,
                    "codigo da credenciadora vazio ou nao textual");
        }
        return node.textValue();
    }

    /** Modo de checkout vigente; obrigatorio com o recurso ligado. */
    public CardEnums.CheckoutMode checkoutMode() {
        if (!enabled()) {
            return null;
        }
        JsonNode node = required(CHECKOUT_MODE);
        if (!node.isTextual()) {
            throw new IllegalStateException(CHECKOUT_MODE + ": esperado texto, veio " + node.getNodeType());
        }
        try {
            return CardEnums.CheckoutMode.valueOf(node.textValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(CHECKOUT_MODE + ": modo desconhecido '" + node.textValue()
                    + "' (esperado PAYMENT_LINK ou HOSTED_CHECKOUT)");
        }
    }

    /** Tolerancia (em reais, >= 0) para divergencia liquida na conciliacao pelo extrato. */
    public BigDecimal statementTolerance() {
        JsonNode node = required(STATEMENT_TOLERANCE);
        if (!node.isNumber()) {
            throw new IllegalStateException(STATEMENT_TOLERANCE + ": esperado numero decimal, veio "
                    + node.getNodeType());
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0) {
            throw new IllegalStateException(STATEMENT_TOLERANCE + ": tolerancia nao pode ser negativa: " + value);
        }
        return value;
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao pagamento com cartao");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}
