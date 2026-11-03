package br.com.ziipsan.platform.infra.events;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validação da carga de um evento contra o subconjunto de JSON Schema que o
 * catálogo admite (P1.38): objeto com {@code properties}, {@code required} e
 * {@code additionalProperties}; tipos {@code string}, {@code integer},
 * {@code number}, {@code boolean}, {@code object} e {@code array};
 * {@code enum} e {@code format} ({@code uuid}, {@code date}, {@code date-time}).
 * Sem dependência de biblioteca externa de JSON Schema: o subconjunto é
 * intencionalmente pequeno para a validação ser exata e auditável.
 *
 * <p>Devolve a lista de erros (vazia = carga válida); quem publica transforma
 * erro em recusa (ver {@link BusinessEventCatalog#requirePublishable}).
 */
public final class BusinessEventPayloadValidator {

    private BusinessEventPayloadValidator() {
    }

    public static List<String> validate(String payloadJson, JsonNode schema) {
        List<String> errors = new ArrayList<>();
        if (schema == null || schema.isMissingNode() || !schema.isObject()) {
            errors.add("esquema do evento ausente ou nao e objeto");
            return errors;
        }
        JsonNode payload;
        try {
            payload = Json.readTree(payloadJson);
        } catch (IllegalStateException e) {
            errors.add("carga nao e JSON valido");
            return errors;
        }
        if (payload.isMissingNode()) {
            errors.add("carga ausente");
            return errors;
        }
        validateNode(payload, schema, "$", errors);
        return List.copyOf(errors);
    }

    private static void validateNode(JsonNode value, JsonNode schema, String path, List<String> errors) {
        JsonNode type = schema.path("type");
        if (type.isTextual()) {
            switch (type.asText()) {
                case "object" -> validateObject(value, schema, path, errors);
                case "array" -> validateArray(value, schema, path, errors);
                case "string" -> {
                    if (!value.isTextual()) {
                        errors.add(path + " deve ser string");
                    } else {
                        validateFormat(value.asText(), schema.path("format"), path, errors);
                    }
                }
                case "integer" -> {
                    if (!value.isIntegralNumber()) {
                        errors.add(path + " deve ser inteiro");
                    }
                }
                case "number" -> {
                    if (!value.isNumber()) {
                        errors.add(path + " deve ser numero");
                    }
                }
                case "boolean" -> {
                    if (!value.isBoolean()) {
                        errors.add(path + " deve ser booleano");
                    }
                }
                default -> errors.add(path + " tem tipo nao admitido no catalogo: " + type.asText());
            }
        } else if (type.isMissingNode() && value.isObject()) {
            validateObject(value, schema, path, errors);
        }
        JsonNode enumNode = schema.path("enum");
        if (enumNode.isArray() && !enumNode.isEmpty()) {
            boolean match = false;
            for (JsonNode candidate : enumNode) {
                if (candidate.equals(value)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                errors.add(path + " fora do enum declarado");
            }
        }
    }

    private static void validateObject(JsonNode value, JsonNode schema, String path, List<String> errors) {
        if (!value.isObject()) {
            errors.add(path + " deve ser objeto");
            return;
        }
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode field : required) {
                if (!field.isTextual()) {
                    continue;
                }
                JsonNode present = value.path(field.asText());
                if (present.isMissingNode() || present.isNull()) {
                    errors.add(path + "." + field.asText() + " e obrigatorio");
                }
            }
        }
        JsonNode properties = schema.path("properties");
        boolean closed = schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean();
        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode fieldSchema = properties.path(field.getKey());
                if (fieldSchema.isMissingNode()) {
                    if (closed) {
                        errors.add(path + "." + field.getKey() + " nao esta no esquema");
                    }
                } else {
                    validateNode(field.getValue(), fieldSchema, path + "." + field.getKey(), errors);
                }
            }
        } else if (closed && value.size() > 0) {
            errors.add(path + " tem campos fora do esquema");
        }
    }

    private static void validateArray(JsonNode value, JsonNode schema, String path, List<String> errors) {
        if (!value.isArray()) {
            errors.add(path + " deve ser array");
            return;
        }
        JsonNode items = schema.path("items");
        if (items.isObject()) {
            int i = 0;
            for (JsonNode item : value) {
                validateNode(item, items, path + "[" + i + "]", errors);
                i++;
            }
        }
    }

    private static void validateFormat(String text, JsonNode format, String path, List<String> errors) {
        if (!format.isTextual()) {
            return;
        }
        try {
            switch (format.asText()) {
                case "uuid" -> UUID.fromString(text);
                case "date" -> LocalDate.parse(text);
                case "date-time" -> OffsetDateTime.parse(text);
                default -> errors.add(path + " tem format nao admitido no catalogo: " + format.asText());
            }
        } catch (RuntimeException e) {
            errors.add(path + " nao segue o format " + format.asText());
        }
    }
}
