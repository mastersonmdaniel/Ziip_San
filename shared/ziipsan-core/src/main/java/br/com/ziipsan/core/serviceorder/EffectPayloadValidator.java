package br.com.ziipsan.core.serviceorder;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates an effect payload against the lightweight schema declared on
 * the service type ({@code effect_schema}).
 *
 * <p>Schema shape (JSON object, both keys optional):
 * <pre>
 * {"required": ["fieldA", "fieldB"],
 *  "properties": {"fieldA": "string", "fieldB": "integer", "fieldC": "number",
 *                 "fieldD": "boolean"}}
 * </pre>
 * Unknown fields are allowed (the owning module may extend the payload);
 * missing required fields and mistyped values are refused. A null schema
 * accepts any payload, including null.
 */
public final class EffectPayloadValidator {

    private EffectPayloadValidator() {
    }

    public static void validate(String effectSchemaJson, String payloadJson, String field) {
        JsonNode schema = Json.readTree(effectSchemaJson);
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }
        if (!schema.isObject()) {
            throw ApiException.Validation.of(new ApiException.FieldError(field, "INVALID_SCHEMA",
                    "O esquema do efeito nao e um objeto JSON."));
        }
        JsonNode payload = Json.readTree(payloadJson);
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            payload = Json.readTree("{}");
        }
        if (!payload.isObject()) {
            throw ApiException.Validation.of(new ApiException.FieldError(field, "INVALID_PAYLOAD",
                    "A carga do efeito precisa ser um objeto JSON."));
        }
        List<ApiException.FieldError> errors = new ArrayList<>();
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode name : required) {
                String key = name.asText();
                if (!payload.has(key) || payload.path(key).isNull()) {
                    errors.add(new ApiException.FieldError(field + "." + key, "REQUIRED",
                            "Campo obrigatorio da carga do efeito: " + key + "."));
                }
            }
        }
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            for (String key : properties.propertyNames()) {
                JsonNode value = payload.path(key);
                if (value.isMissingNode() || value.isNull()) {
                    continue;
                }
                String expected = properties.path(key).asText();
                boolean ok = switch (expected) {
                    case "string" -> value.isString();
                    case "integer" -> value.isIntegralNumber();
                    case "number" -> value.isNumber();
                    case "boolean" -> value.isBoolean();
                    default -> true;
                };
                if (!ok) {
                    errors.add(new ApiException.FieldError(field + "." + key, "INVALID_TYPE",
                            "O campo " + key + " deve ser " + expected + "."));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException.Validation("A carga do efeito nao segue o esquema do tipo.",
                    errors, List.of());
        }
    }
}
