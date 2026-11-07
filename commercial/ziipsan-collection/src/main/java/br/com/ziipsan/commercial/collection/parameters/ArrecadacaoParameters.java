package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Parametros da arrecadacao ampliada (ED-26), no mesmo padrao de
 * {@link CollectionParameters} (P3.1): JSON sempre pela classe {@code Json} do core.
 *
 * <ul>
 *   <li>{@code pay.partial_settlement_order} - ordem da quitacao em cascata da fatura
 *       agrupadora quando o valor pago e menor que o total. SEM padrao presumido:
 *       ausencia e erro explicito ({@link MissingParameterException}), porque presumir
 *       uma ordem mudaria quais contas de quais unidades sao baixadas.</li>
 *   <li>{@code pay.own_cashier_enabled} - caixa proprio do prestador (JOR-13, PD-07).
 *       A spec da ED-26 diz "desligado por padrao": ausencia vale {@code false}
 *       (unica excecao deliberada e documentada a regra de ausencia = erro).</li>
 * </ul>
 *
 * Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   pay.partial_settlement_order  STRING  "DUE_DATE_ASC" | "ISSUE_DATE_ASC" | "AMOUNT_ASC" | "AMOUNT_DESC"
 *   pay.own_cashier_enabled       BOOLEAN false
 * </pre>
 */
public final class ArrecadacaoParameters {

    public static final String PARTIAL_SETTLEMENT_ORDER = "pay.partial_settlement_order";
    public static final String OWN_CASHIER_ENABLED = "pay.own_cashier_enabled";

    /** Ordem de aplicacao do valor pago entre as contas de um documento agrupador. */
    public enum PartialSettlementOrder { DUE_DATE_ASC, ISSUE_DATE_ASC, AMOUNT_ASC, AMOUNT_DESC }

    private final Map<String, String> jsonByKey;

    private ArrecadacaoParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static ArrecadacaoParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new ArrecadacaoParameters(Map.copyOf(jsonByKey));
    }

    public PartialSettlementOrder partialSettlementOrder() {
        String json = jsonByKey.get(PARTIAL_SETTLEMENT_ORDER);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(PARTIAL_SETTLEMENT_ORDER,
                    "nenhum valor entregue a arrecadacao ampliada");
        }
        JsonNode node = Json.readTree(json);
        if (!node.isTextual()) {
            throw new IllegalStateException(PARTIAL_SETTLEMENT_ORDER
                    + ": esperado texto com o nome da ordem, veio " + node.getNodeType());
        }
        try {
            return PartialSettlementOrder.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(PARTIAL_SETTLEMENT_ORDER
                    + ": ordem desconhecida: " + node.asText(), e);
        }
    }

    /** Caixa proprio ligado? Desligado por padrao (ED-26/PD-07): ausencia = false. */
    public boolean ownCashierEnabled() {
        String json = jsonByKey.get(OWN_CASHIER_ENABLED);
        if (json == null || json.isBlank()) {
            return false;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) {
            throw new IllegalStateException(OWN_CASHIER_ENABLED
                    + ": esperado booleano, veio " + node.getNodeType());
        }
        return node.booleanValue();
    }
}
