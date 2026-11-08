package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Parametro do debito automatico legado (P3.24), no mesmo padrao de
 * {@code CollectionParameters} (P3.1) e {@code PixParameters} (P3.2):
 * JSON lido SOMENTE pela classe {@link Json} do core e ausencia =
 * {@link MissingParameterException}, nunca valor presumido.
 *
 * <pre>
 *   pay.legacy_debit_enabled  BOOLEAN  (classe d)
 * </pre>
 *
 * <p>Classe d: registra a regra do legado. A DATA DE DESLIGAMENTO nao e
 * um parametro e nao pode ser digitada: ela e deduzida em
 * {@code BcbDebitRegimeRules} da regra vigente do BCB. Qualquer chave de
 * data presente no mapa e IGNORADA por esta classe de proposito - o teste
 * {@code dataDigitadaNoParametroNaoMudaADataDeduzida} trava isso.
 */
public final class LegacyDebitParameters {

    public static final String LEGACY_DEBIT_ENABLED = "pay.legacy_debit_enabled";

    private final Map<String, String> jsonByKey;

    private LegacyDebitParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static LegacyDebitParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new LegacyDebitParameters(Map.copyOf(jsonByKey));
    }

    /** FEBRABAN 150 legado ligado para este tenant? Ausencia e erro explicito. */
    public boolean legacyDebitEnabled() {
        JsonNode node = required(LEGACY_DEBIT_ENABLED);
        if (!node.isBoolean()) {
            throw new IllegalStateException(LEGACY_DEBIT_ENABLED + ": esperado booleano, veio "
                    + node.getNodeType());
        }
        return node.booleanValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao debito legado");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}
