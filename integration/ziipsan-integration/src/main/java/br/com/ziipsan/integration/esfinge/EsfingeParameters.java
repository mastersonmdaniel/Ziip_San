package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Parametros da remessa e-Sfinge (ED-33), no padrao de ActiveDebtParameters
 * (ED-31) e DunningParameters (P4.1): JSON sempre pela classe Json do core.
 *
 * <ul>
 *   <li>{@code esfinge.layout_version} — versao do leiaute vigente do TCE/SC
 *       que a remessa declara e contra a qual e validada. SEM padrao de
 *       fabrica e SEM valor neste codigo: o leiaute vigente nao esta nas
 *       fontes desta entrega (PENDENCIA DECLARADA — ver EsfingeLayout);
 *       ausencia e erro explicito, nunca uma versao chutada. A geracao
 *       recusa leiaute cuja versao difira deste parametro;</li>
 *   <li>{@code esfinge.layout_norm_reference} — referencia da norma do
 *       leiaute (ex.: a instrucao normativa do TCE/SC citada nas fontes do
 *       plano para a obrigacao e-Sfinge). Opcional NESTA classe porque quem
 *       carrega a norma e o proprio leiaute registrado; quando presente,
 *       tem de ser texto nao vazio.</li>
 * </ul>
 */
public final class EsfingeParameters {

    public static final String LAYOUT_VERSION = "esfinge.layout_version";
    public static final String LAYOUT_NORM_REFERENCE = "esfinge.layout_norm_reference";

    private final Map<String, String> jsonByKey;

    private EsfingeParameters(Map<String, String> jsonByKey) { this.jsonByKey = jsonByKey; }

    public static EsfingeParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        return new EsfingeParameters(Map.copyOf(jsonByKey));
    }

    /** Versao do leiaute vigente (TCE/SC): obrigatoria, sem constante neste codigo. */
    public String layoutVersion() { return requiredText(LAYOUT_VERSION); }

    /** Referencia da norma do leiaute, quando parametrizada; null quando ausente. */
    public String layoutNormReference() {
        String json = jsonByKey.get(LAYOUT_NORM_REFERENCE);
        if (json == null || json.isBlank()) return null;
        JsonNode node = Json.readTree(json);
        if (!node.isTextual() || node.asText().isBlank())
            throw new IllegalStateException(LAYOUT_NORM_REFERENCE + ": esperado texto nao vazio");
        return node.asText();
    }

    private String requiredText(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) throw new MissingParameterException(key, "nenhum valor entregue a remessa e-Sfinge");
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) throw new MissingParameterException(key, "valor JSON e nulo");
        if (!node.isTextual() || node.asText().isBlank()) throw new IllegalStateException(key + ": esperado texto nao vazio");
        return node.asText();
    }
}
