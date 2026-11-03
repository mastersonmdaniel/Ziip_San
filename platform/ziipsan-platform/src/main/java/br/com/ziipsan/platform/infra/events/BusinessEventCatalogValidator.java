package br.com.ziipsan.platform.infra.events;

import br.com.ziipsan.security.auth.PermissionCatalog;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validação estrutural do catálogo (P1.38), executada por
 * {@link BusinessEventCatalogLoader} antes de qualquer uso: nome pontuado e
 * único por versão, versão a partir de 1, permissão exigida dentro do catálogo
 * da P0.13 (nunca inventada), consumidores dentro dos consumidores declarados
 * do pacote, esquema da carga no subconjunto admitido e sem campo de dado
 * pessoal.
 *
 * <p>Regra de dado pessoal: a carga carrega só identificadores e códigos. Um
 * campo cujo nome contenha um token de dado pessoal (cpf, cnpj, e-mail,
 * telefone, endereço, nascimento, senha, nome) é recusado — exceto quando o
 * nome termina em identificador ou código ({@code *Id}, {@code *Code},
 * {@code *_id}, {@code *_code}), que é exatamente o que a carga pode carregar
 * (ex.: {@code documentTypeId} é um código, não o documento).
 */
public final class BusinessEventCatalogValidator {

    private static final Set<String> PERSONAL_TOKENS = Set.of(
            "cpf", "cnpj", "email", "mail", "phone", "telefone", "celular",
            "address", "endereco", "birth", "nascimento", "password", "senha",
            "secret", "rg", "pis", "nis", "fullname", "nome", "name");

    private static final Set<String> SCHEMA_TYPES = Set.of(
            "string", "integer", "number", "boolean", "object", "array");

    private static final Set<String> SCHEMA_FORMATS = Set.of("uuid", "date", "date-time");

    private BusinessEventCatalogValidator() {
    }

    public static void validate(BusinessEventCatalog catalog) {
        if (catalog == null) {
            throw new IllegalStateException("O catalogo de eventos e obrigatorio");
        }
        Set<String> declaredTasks = new HashSet<>();
        for (BusinessEventCatalog.DeclaredConsumer consumer : catalog.declaredConsumers()) {
            require(consumer.task() != null && !consumer.task().isBlank(),
                    "Consumidor declarado sem tarefa");
            require(consumer.name() != null && !consumer.name().isBlank(),
                    "Consumidor declarado sem nome: " + consumer.task());
            require(declaredTasks.add(consumer.task()),
                    "Consumidor declarado duplicado: " + consumer.task());
        }
        Set<String> eventKeys = new HashSet<>();
        for (BusinessEventCatalog.BusinessEvent event : catalog.events()) {
            require(event.name() != null && event.name().matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"),
                    "Nome de evento invalido (esperado a.b.c): " + event.name());
            require(event.version() >= 1, "Versao do evento deve ser >= 1: " + event.name());
            require(eventKeys.add(event.name() + "@v" + event.version()),
                    "Evento duplicado no catalogo: " + event.name() + " v" + event.version());
            require(event.description() != null && !event.description().isBlank(),
                    "Descricao obrigatoria no evento: " + event.name());
            require(event.originTask() != null && !event.originTask().isBlank(),
                    "Tarefa de origem obrigatoria no evento: " + event.name());
            require(PermissionCatalog.isPermission(event.requiredPermission()),
                    "Permissao exigida fora do catalogo da P0.13 no evento " + event.name()
                            + ": " + event.requiredPermission());
            require(event.consumers() != null && !event.consumers().isEmpty(),
                    "Evento sem consumidor declarado: " + event.name());
            for (String consumer : event.consumers()) {
                require(declaredTasks.contains(consumer),
                        "Consumidor do evento " + event.name() + " nao esta declarado no pacote: " + consumer);
            }
            validateSchema(event.payloadSchema(), event.name(), "$");
        }
        for (BusinessEventCatalog.CatalogGap gap : catalog.gaps()) {
            require(gap.name() != null && !gap.name().isBlank(), "Lacuna sem nome");
            require(gap.reason() != null && !gap.reason().isBlank(), "Lacuna sem motivo: " + gap.name());
            require(!catalog.isCatalogued(gap.name()),
                    "Lacuna registrada para evento que esta no catalogo: " + gap.name());
        }
    }

    private static void validateSchema(JsonNode schema, String eventName, String path) {
        require(schema != null && schema.isObject(), "Esquema da carga ausente no evento: " + eventName);
        JsonNode type = schema.path("type");
        require(type.isMissingNode() || "object".equals(type.asText()),
                "Esquema da carga do evento " + eventName + " deve ser objeto: " + path);
        JsonNode properties = schema.path("properties");
        require(properties.isObject() && properties.size() > 0,
                "Esquema da carga do evento " + eventName + " deve declarar properties: " + path);
        Iterator<Map.Entry<String, JsonNode>> fields = properties.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            validateFieldName(field.getKey(), eventName);
            JsonNode fieldSchema = field.getValue();
            require(fieldSchema.isObject(), "Esquema do campo invalido no evento " + eventName + ": " + path);
            JsonNode fieldType = fieldSchema.path("type");
            require(fieldType.isTextual() && SCHEMA_TYPES.contains(fieldType.asText()),
                    "Tipo nao admitido no catalogo no evento " + eventName + ": "
                            + path + "." + field.getKey() + " = " + fieldType.asText());
            JsonNode format = fieldSchema.path("format");
            require(format.isMissingNode()
                            || ("string".equals(fieldType.asText()) && format.isTextual()
                                    && SCHEMA_FORMATS.contains(format.asText())),
                    "Format nao admitido no catalogo no evento " + eventName + ": "
                            + path + "." + field.getKey());
            JsonNode enumNode = fieldSchema.path("enum");
            require(enumNode.isMissingNode() || (enumNode.isArray() && enumNode.size() > 0),
                    "Enum vazio no evento " + eventName + ": " + path + "." + field.getKey());
            if ("object".equals(fieldType.asText())) {
                validateSchema(fieldSchema, eventName, path + "." + field.getKey());
            }
            if ("array".equals(fieldType.asText())) {
                JsonNode items = fieldSchema.path("items");
                require(items.isObject() && items.path("type").isTextual()
                                && SCHEMA_TYPES.contains(items.path("type").asText()),
                        "Items do array sem tipo admitido no evento " + eventName + ": "
                                + path + "." + field.getKey());
                // Array de objetos (ou de arrays de objetos): o esquema dos
                // itens passa pela mesma validação — sem isso, um campo de
                // dado pessoal dentro de um item escaparia da regra da carga.
                validateItemsSchema(items, eventName, path + "." + field.getKey());
            }
        }
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode field : required) {
                require(field.isTextual() && properties.has(field.asText()),
                        "Campo obrigatorio fora de properties no evento " + eventName + ": " + field.asText());
            }
        }
    }

    private static void validateItemsSchema(JsonNode items, String eventName, String path) {
        JsonNode current = items;
        while (current.isObject() && "array".equals(current.path("type").asText())) {
            current = current.path("items");
            require(current.isObject() && current.path("type").isTextual()
                            && SCHEMA_TYPES.contains(current.path("type").asText()),
                    "Items do array sem tipo admitido no evento " + eventName + ": " + path);
        }
        if (current.isObject() && "object".equals(current.path("type").asText())) {
            validateSchema(current, eventName, path + "[]");
        }
    }

    private static void validateFieldName(String fieldName, String eventName) {
        String lower = fieldName.toLowerCase();
        boolean identifierOrCode = lower.endsWith("id") || lower.endsWith("code")
                || lower.endsWith("_id") || lower.endsWith("_code");
        if (identifierOrCode) {
            return;
        }
        for (String token : splitTokens(fieldName)) {
            require(!PERSONAL_TOKENS.contains(token),
                    "Campo de dado pessoal na carga do evento " + eventName + ": " + fieldName);
        }
    }

    /** Quebra o nome em tokens por '_'/'-' e por transição minúscula→maiúscula, em minúsculas. */
    private static List<String> splitTokens(String fieldName) {
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (c == '_' || c == '-') {
                if (!current.isEmpty()) {
                    tokens.add(current.toString().toLowerCase());
                    current.setLength(0);
                }
            } else if (Character.isUpperCase(c) && !current.isEmpty()
                    && !Character.isUpperCase(current.charAt(current.length() - 1))) {
                tokens.add(current.toString().toLowerCase());
                current.setLength(0);
                current.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (!current.isEmpty()) {
                tokens.add(current.toString().toLowerCase());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString().toLowerCase());
        }
        return tokens;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Catalogo de eventos invalido: " + message);
        }
    }
}
