package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Validação de {@code attributes} de feição contra o JSON Schema do tipo
 * (GIS-CADASTRO-TECNICO §3.3), no lado Java.
 *
 * <p>Espelha o gatilho {@code gis_validate_attributes} da V13_1, no mesmo
 * subconjunto (o banco não tem {@code pg_jsonschema}): {@code required},
 * {@code type} (string, integer, number, boolean, object, array, null),
 * {@code enum} e {@code $ref} no formato {@code "gis_value_list:<list_code>"}
 * (lista de {@code gis_value_list}). {@code additionalProperties: false}
 * recusa chave não declarada em {@code properties}; sem ele, vale o padrão
 * do JSON Schema (chave extra permitida).
 *
 * <p>A API repete a validação para devolver erro por campo
 * ({@code errors[].code} em inglês, como pede o §3.3); o banco é a última
 * linha, porque há escrita fora da API (QGIS, importação). Os dois lados
 * precisam continuar iguais: qualquer mudança aqui exige a mesma mudança
 * na função PL/pgSQL e vice-versa.
 */
public final class GisAttributesValidator {

    /** Um campo recusado, com o código em inglês (contrato do §3.3). */
    public record FieldError(String field, String code) {
    }

    private GisAttributesValidator() {
    }

    /**
     * Valida {@code attributesJson} contra {@code schemaJson}.
     *
     * @param attributesJson JSON de attributes; nulo ou em branco vale {@code {}}
     *                       (o gatilho do banco faz a mesma coerção antes de validar)
     * @param schemaJson     JSON Schema do tipo (coluna {@code attributes_schema});
     *                       nulo é erro de programação, não de dado
     * @param valueListCodes resolve o código de uma lista de valores para o
     *                       conjunto de códigos ativos dela; nulo com
     *                       {@code $ref} no schema devolve erro
     * @return erros encontrados; lista vazia significa válido
     */
    public static List<FieldError> validate(String attributesJson, String schemaJson,
                                            Function<String, Set<String>> valueListCodes) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new IllegalArgumentException("attributes_schema do tipo ausente");
        }
        JsonNode attributes = (attributesJson == null || attributesJson.isBlank())
                ? Json.readTree("{}") : Json.readTree(attributesJson);
        JsonNode schema = Json.readTree(schemaJson);
        List<FieldError> errors = new ArrayList<>();
        if (!attributes.isObject()) {
            errors.add(new FieldError("", "attributes_not_object"));
            return errors;
        }

        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode key : required) {
                if (!attributes.has(key.asString())) {
                    errors.add(new FieldError(key.asString(), "required"));
                }
            }
        }

        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode prop = entry.getValue();
                if (!attributes.has(key)) {
                    continue;
                }
                JsonNode value = attributes.get(key);

                JsonNode ref = prop.get("$ref");
                if (ref != null && ref.isTextual()) {
                    String refText = ref.asString();
                    if (!refText.startsWith("gis_value_list:")) {
                        errors.add(new FieldError(key, "unsupported_ref"));
                    } else if (valueListCodes == null) {
                        errors.add(new FieldError(key, "value_list_unresolved"));
                    } else {
                        String listCode = refText.substring("gis_value_list:".length());
                        if (!value.isTextual()) {
                            errors.add(new FieldError(key, "value_list_not_text"));
                        } else if (!valueListCodes.apply(listCode).contains(value.asString())) {
                            errors.add(new FieldError(key, "value_list_unknown_code"));
                        }
                    }
                }

                JsonNode enumNode = prop.get("enum");
                if (enumNode != null && enumNode.isArray()) {
                    boolean found = false;
                    for (JsonNode allowed : enumNode) {
                        if (allowed.equals(value)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        errors.add(new FieldError(key, "enum"));
                    }
                }

                JsonNode type = prop.get("type");
                if (type != null && type.isTextual() && !matchesType(type.asString(), value)) {
                    errors.add(new FieldError(key, "type"));
                }
            }

            JsonNode additional = schema.get("additionalProperties");
            if (additional != null && additional.isBoolean() && !additional.asBoolean()) {
                for (String key : attributes.propertyNames()) {
                    if (!properties.has(key)) {
                        errors.add(new FieldError(key, "additional_property"));
                    }
                }
            }
        }
        return errors;
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> false;
        };
    }
}
