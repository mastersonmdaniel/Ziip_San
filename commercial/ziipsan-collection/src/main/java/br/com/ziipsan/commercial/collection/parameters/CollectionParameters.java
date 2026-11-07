package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Leitura tipada dos parametros do tenant consumidos pela arrecadacao/retorno (P3.1),
 * no mesmo padrao de {@code BillingParameters} (P2.5): o chamador resolve os valores
 * vigentes e aceitos e entrega o JSON de cada chave; esta classe interpreta - sempre
 * pela classe {@link Json} do core - e valida.
 *
 * <p>Principio: parametro ausente e erro explicito ({@link MissingParameterException}),
 * nunca valor assumido. Em especial {@code pay.withholding_enabled} NAO tem padrao:
 * a retencao na fonte so pode ser ligada por parametro depois do parecer juridico
 * sobre o alcance (PD-10); presumir "desligado" esconderia um parametro nao publicado
 * e presumir "ligado" ativaria retencao sem parecer.
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   pay.adjust_tolerance     DECIMAL  5.00   (tolerancia de ajuste da baixa, em reais)
 *   pay.withholding_enabled  BOOLEAN  false  (retencao IN RFB 1.234/2012; so apos PD-10)
 * </pre>
 */
public final class CollectionParameters {

    public static final String ADJUST_TOLERANCE = "pay.adjust_tolerance";
    public static final String WITHHOLDING_ENABLED = "pay.withholding_enabled";

    private final Map<String, String> jsonByKey;

    private CollectionParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static CollectionParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new CollectionParameters(Map.copyOf(jsonByKey));
    }

    /** Tolerancia (em reais, >= 0) para ajuste de diferenca de baixa na conta seguinte. */
    public BigDecimal adjustTolerance() {
        JsonNode node = required(ADJUST_TOLERANCE);
        if (!node.isNumber()) {
            throw new IllegalStateException(ADJUST_TOLERANCE + ": esperado numero decimal, veio "
                    + node.getNodeType());
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0) {
            throw new IllegalStateException(ADJUST_TOLERANCE + ": tolerancia nao pode ser negativa: " + value);
        }
        return value;
    }

    /** Retencao na fonte ligada? Ausencia e erro explicito (ver javadoc da classe). */
    public boolean withholdingEnabled() {
        JsonNode node = required(WITHHOLDING_ENABLED);
        if (!node.isBoolean()) {
            throw new IllegalStateException(WITHHOLDING_ENABLED + ": esperado booleano, veio "
                    + node.getNodeType());
        }
        return node.booleanValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue a arrecadacao");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}
