package br.com.ziipsan.integration.sinisa;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogo carregavel do mapa de variaveis SINISA (P4.2): a base sobre a qual a
 * ED-48 monta o catalogo SINISA vigente completo e o painel.
 *
 * <p>O catalogo e <b>dado</b>, nao codigo: e carregado de um documento JSON (lido so
 * pela classe {@code Json} do core) no formato:
 * <pre>
 * [
 *   {"key": "AGUA.VOLUME_FATURADO", "officialCode": null, "form": "TECNICO",
 *    "name": "Volume de agua faturado", "unit": "m3", "sourceKind": "METERING",
 *    "sourceExpression": "metering: soma de consumption_records.billed_consumption ..."}
 * ]
 * </pre>
 * A carga recusa: chave duplicada, formulario ou natureza fora do vocabulario,
 * variavel sem origem declarada (a validacao vive em {@link SinisaVariable}) e
 * documento que nao seja um array de objetos. {@code officialCode} ausente ou nulo
 * significa "glossario do ciclo ainda nao carregado" e e aceito; codigo em branco,
 * nao.
 */
public final class SinisaCatalog {

    private final Map<String, SinisaVariable> byKey;

    private SinisaCatalog(Map<String, SinisaVariable> byKey) {
        this.byKey = byKey;
    }

    /** Carrega o catalogo do documento JSON. Documento invalido e erro explicito. */
    public static SinisaCatalog fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Catalogo SINISA vazio: documento JSON obrigatorio");
        }
        JsonNode root = Json.readTree(json);
        if (!root.isArray()) {
            throw new IllegalArgumentException("Catalogo SINISA invalido: esperado array de variaveis");
        }
        Map<String, SinisaVariable> byKey = new LinkedHashMap<>();
        for (JsonNode item : root) {
            SinisaVariable variable = parse(item);
            if (byKey.putIfAbsent(variable.key(), variable) != null) {
                throw new IllegalArgumentException(
                        "Catalogo SINISA invalido: chave duplicada " + variable.key());
            }
        }
        if (byKey.isEmpty()) {
            throw new IllegalArgumentException("Catalogo SINISA invalido: nenhuma variavel");
        }
        return new SinisaCatalog(Map.copyOf(byKey));
    }

    private static SinisaVariable parse(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new IllegalArgumentException("Catalogo SINISA invalido: variavel deve ser um objeto");
        }
        String key = text(item, "key");
        JsonNode official = item.get("officialCode");
        String officialCode = official == null || official.isNull() ? null : text(item, "officialCode");
        return new SinisaVariable(
                key,
                officialCode,
                parseEnum(item, "form", SinisaForm.class, key),
                text(item, "name"),
                text(item, "unit"),
                parseEnum(item, "sourceKind", SinisaVariable.SourceKind.class, key),
                text(item, "sourceExpression"));
    }

    private static String text(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(
                    "Catalogo SINISA invalido: campo '" + field + "' deve ser texto");
        }
        return node.asText();
    }

    private static <E extends Enum<E>> E parseEnum(JsonNode item, String field, Class<E> type, String key) {
        String value = text(item, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Catalogo SINISA invalido: variavel " + key
                    + ", campo '" + field + "' fora do vocabulario: " + value);
        }
    }

    public Optional<SinisaVariable> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** Variavel pela chave interna; chave desconhecida e erro explicito. */
    public SinisaVariable require(String key) {
        SinisaVariable variable = byKey.get(key);
        if (variable == null) {
            throw new IllegalArgumentException("Variavel SINISA fora do catalogo carregado: " + key);
        }
        return variable;
    }

    /** Variaveis na ordem de carga do documento (ordem deterministica do arquivo de envio). */
    public List<SinisaVariable> variables() {
        return List.copyOf(new ArrayList<>(byKey.values()));
    }

    public int size() {
        return byKey.size();
    }
}
