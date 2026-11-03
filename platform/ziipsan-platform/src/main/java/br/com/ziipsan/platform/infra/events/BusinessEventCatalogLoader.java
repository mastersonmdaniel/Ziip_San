package br.com.ziipsan.platform.infra.events;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê a seção {@code events} (mais {@code declaredEventConsumers} e
 * {@code eventGaps}) de um {@code vertical.json} para
 * {@link BusinessEventCatalog} e valida com
 * {@link BusinessEventCatalogValidator}. Ausência da seção significa catálogo
 * vazio — publicar qualquer evento, então, é recusado — nunca catálogo
 * implícito.
 */
public final class BusinessEventCatalogLoader {

    private BusinessEventCatalogLoader() {
    }

    public static BusinessEventCatalog load(Path manifestPath) {
        String json;
        try {
            json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler o manifesto: " + manifestPath, e);
        }
        return fromJson(json);
    }

    public static BusinessEventCatalog fromJson(String json) {
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode() || !root.isObject()) {
            throw new IllegalStateException("O manifesto nao e um objeto JSON");
        }
        List<BusinessEventCatalog.DeclaredConsumer> consumers = new ArrayList<>();
        for (JsonNode node : array(root, "declaredEventConsumers")) {
            consumers.add(new BusinessEventCatalog.DeclaredConsumer(
                    text(node, "task"), text(node, "name")));
        }
        List<BusinessEventCatalog.BusinessEvent> events = new ArrayList<>();
        for (JsonNode node : array(root, "events")) {
            List<String> eventConsumers = new ArrayList<>();
            for (JsonNode consumer : array(node, "consumers")) {
                if (!consumer.isTextual()) {
                    throw new IllegalStateException("Consumidor do evento nao textual: " + node.path("name").asText());
                }
                eventConsumers.add(consumer.asText());
            }
            JsonNode schema = node.path("payloadSchema");
            if (schema.isMissingNode()) {
                throw new IllegalStateException("Esquema da carga ausente no evento: " + node.path("name").asText());
            }
            events.add(new BusinessEventCatalog.BusinessEvent(
                    text(node, "name"),
                    node.path("version").asInt(-1),
                    text(node, "description"),
                    schema,
                    text(node, "requiredPermission"),
                    List.copyOf(eventConsumers),
                    text(node, "originTask")));
        }
        List<BusinessEventCatalog.CatalogGap> gaps = new ArrayList<>();
        for (JsonNode node : array(root, "eventGaps")) {
            gaps.add(new BusinessEventCatalog.CatalogGap(text(node, "name"), text(node, "reason")));
        }
        BusinessEventCatalog catalog = new BusinessEventCatalog(
                List.copyOf(events), List.copyOf(consumers), List.copyOf(gaps));
        BusinessEventCatalogValidator.validate(catalog);
        return catalog;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isTextual()) {
            throw new IllegalStateException("Campo obrigatorio ausente ou nao textual: " + field);
        }
        return value.asText();
    }

    private static List<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalStateException("Campo nao e array: " + field);
        }
        List<JsonNode> items = new ArrayList<>();
        value.forEach(items::add);
        return items;
    }
}
