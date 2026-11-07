package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;

/**
 * Leitura tipada dos parametros do tenant consumidos pelo ciclo SINISA (P4.2), no
 * mesmo padrao de {@code BillingParameters} (P2.5): o chamador resolve os valores
 * vigentes e aceitos das versoes de parametro (P0.19/P1.6) e entrega o JSON de cada
 * chave; esta classe so interpreta, sempre pela classe {@code Json} do core.
 *
 * <p>Principio do catalogo de parametros: ausencia e erro explicito
 * ({@link MissingParameterException}), nunca valor assumido em silencio. Sem
 * {@code sinisa.provider_scope} aceito, nenhum ciclo abre: o escopo decide o
 * ambiente de preenchimento no sistema oficial (local ou regional) e nao pode ser
 * presumido.
 *
 * <p>Formato JSON da chave (o mesmo que vai em {@code parameter_versions.value}):
 * <pre>
 *   sinisa.provider_scope   ENUM   "LOCAL" | "REGIONAL"
 * </pre>
 */
public final class SinisaParameters {

    public static final String PROVIDER_SCOPE = "sinisa.provider_scope";

    private final Map<String, String> jsonByKey;

    private SinisaParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    /** @param jsonByKey chave do parametro -> JSON vigente e aceito (texto da coluna {@code value}) */
    public static SinisaParameters fromValues(Map<String, String> jsonByKey) {
        Objects.requireNonNull(jsonByKey, "Mapa de parametros e obrigatorio");
        return new SinisaParameters(Map.copyOf(jsonByKey));
    }

    /** {@code sinisa.provider_scope}: escopo do prestador (LOCAL ou REGIONAL). */
    public SinisaProviderScope providerScope() {
        String json = jsonByKey.get(PROVIDER_SCOPE);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(PROVIDER_SCOPE, "nenhum valor entregue ao ciclo SINISA");
        }
        JsonNode node = Json.readTree(json);
        if (!node.isTextual()) {
            throw new IllegalArgumentException(
                    "Parametro '" + PROVIDER_SCOPE + "' invalido: esperado texto LOCAL ou REGIONAL");
        }
        try {
            return SinisaProviderScope.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Parametro '" + PROVIDER_SCOPE + "' invalido: valor fora do catalogo: " + node.asText());
        }
    }
}
