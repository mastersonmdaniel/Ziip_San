package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Criação e encerramento de itens POR EVENTOS do catálogo da P1.38 (JOR-20) — o contrato
 * que cada módulo usa para registrar trabalho na caixa sem conhecer a caixa por dentro:
 *
 * <ol>
 *   <li>o evento PRECISA estar no catálogo, na versão declarada — fora do catálogo ou em
 *       versão não declarada, a mensagem é recusada (409) e NENHUM item nasce/morre;</li>
 *   <li>o EFEITO do evento no catálogo precisa combinar com a operação: evento de abertura
 *       não encerra, evento de encerramento não abre (409);</li>
 *   <li>a carga é lida pela {@code Json} do core; campo obrigatório ausente é recusa (422)
 *       nomeando o campo — a caixa não inventa responsável, prazo nem objeto.</li>
 * </ol>
 *
 * <p><b>Convenção de carga DESTA porta</b> (ver {@link WorkOutboxMessage}): abertura —
 * {@code eventVersion} (inteiro), {@code workItemType} (nome de {@link WorkItemType}),
 * {@code objectType}, {@code objectId}, {@code originModule}, {@code requesterId},
 * {@code recordAddress}; opcionais — {@code assigneeId}, {@code eligibleApproverIds}
 * (array de UUID), {@code requiredPermission}, {@code dueAt} (instante ISO-8601),
 * {@code approval} (booleano: nasce {@code approval_task} pendente ligada ao item).
 * Ao menos um endereçamento (assignee/elegíveis/permissão) é obrigatório — a validação
 * é a do próprio {@link WorkItem}. Encerramento — {@code eventVersion}, {@code workItemId},
 * {@code closeReason} (código; ex.: {@code COMPLETED}, {@code CANCELLED},
 * {@code APPROVED}, {@code REJECTED}, {@code SUPERSEDED}).
 */
public final class WorkItemIngestRule {

    private WorkItemIngestRule() {
    }

    /** Item pronto para gravar em {@code work_items} (+ a approval_task, se {@code approval}). */
    public record OpenDraft(UUID sourceMessageId, UUID tenantId, WorkItemType type, String objectType,
                            UUID objectId, String originModule, UUID requesterId, UUID assigneeId,
                            Set<UUID> eligibleApproverIds, String requiredPermission, Instant dueAt,
                            String recordAddress, boolean approval,
                            String sourceEventName, int sourceEventVersion) {
    }

    /** Encerramento pronto para aplicar ao item {@code workItemId}. */
    public record CloseDraft(UUID sourceMessageId, UUID tenantId, UUID workItemId, String closeReason,
                             String sourceEventName, int sourceEventVersion) {
    }

    public static OpenDraft draftOpen(WorkOutboxMessage message, WorkEventCatalog catalog) {
        WorkCatalogEvent event = requireEvent(message, catalog);
        if (event.effect() != WorkCatalogEvent.Effect.WORK_ITEM_OPENED) {
            throw new ApiException.Conflict("event-effect-mismatch",
                    "O evento " + message.type() + " não abre item de trabalho no catálogo.");
        }
        JsonNode payload = Json.readTree(message.payloadJson());
        WorkItemType type;
        try {
            type = WorkItemType.valueOf(requiredText(payload, "workItemType"));
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "workItemType", "invalid", "Tipo de item fora do contrato da caixa: " + payload.get("workItemType")));
        }
        UUID assigneeId = optionalUuid(payload, "assigneeId");
        Set<UUID> eligible = optionalUuidSet(payload, "eligibleApproverIds");
        String requiredPermission = optionalText(payload, "requiredPermission");
        if (assigneeId == null && eligible.isEmpty() && requiredPermission == null) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "assigneeId", "required",
                    "A carga do evento não endereça o item a ninguém (responsável, elegíveis ou permissão)."));
        }
        return new OpenDraft(message.id(), message.tenantId(), type,
                requiredText(payload, "objectType"), requiredUuid(payload, "objectId"),
                requiredText(payload, "originModule"), requiredUuid(payload, "requesterId"),
                assigneeId, eligible, requiredPermission, optionalInstant(payload, "dueAt"),
                requiredText(payload, "recordAddress"), optionalBoolean(payload, "approval"),
                event.name(), event.version());
    }

    public static CloseDraft draftClose(WorkOutboxMessage message, WorkEventCatalog catalog) {
        WorkCatalogEvent event = requireEvent(message, catalog);
        if (event.effect() != WorkCatalogEvent.Effect.WORK_ITEM_CLOSED) {
            throw new ApiException.Conflict("event-effect-mismatch",
                    "O evento " + message.type() + " não encerra item de trabalho no catálogo.");
        }
        JsonNode payload = Json.readTree(message.payloadJson());
        return new CloseDraft(message.id(), message.tenantId(),
                requiredUuid(payload, "workItemId"), requiredText(payload, "closeReason"),
                event.name(), event.version());
    }

    private static WorkCatalogEvent requireEvent(WorkOutboxMessage message, WorkEventCatalog catalog) {
        if (message == null || catalog == null) {
            throw new IllegalArgumentException("mensagem e catálogo são obrigatórios");
        }
        JsonNode payload = Json.readTree(message.payloadJson());
        int version = requiredInt(payload, "eventVersion");
        return catalog.find(message.type(), version).orElseThrow(() ->
                new ApiException.Conflict("event-not-in-catalog",
                        "Evento fora do catálogo de eventos ou em versão não declarada: " + message.type()));
    }

    private static int requiredInt(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isIntegralNumber()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "required", "Campo obrigatório ausente ou inválido na carga do evento: " + field));
        }
        return node.asInt();
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "required", "Campo obrigatório ausente ou inválido na carga do evento: " + field));
        }
        return node.asText();
    }

    private static String optionalText(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Campo inválido na carga do evento: " + field));
        }
        return node.asText();
    }

    private static UUID requiredUuid(JsonNode payload, String field) {
        UUID value = optionalUuid(payload, field);
        if (value == null) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "required", "Campo obrigatório ausente ou inválido na carga do evento: " + field));
        }
        return value;
    }

    private static UUID optionalUuid(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Identificador inválido na carga do evento: " + field));
        }
        try {
            return UUID.fromString(node.asText());
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Identificador inválido na carga do evento: " + field));
        }
    }

    private static Set<UUID> optionalUuidSet(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return Set.of();
        }
        if (!node.isArray()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Campo inválido na carga do evento (esperado array de UUID): " + field));
        }
        Set<UUID> values = new LinkedHashSet<>();
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        field, "invalid", "Identificador inválido na carga do evento: " + field));
            }
            try {
                values.add(UUID.fromString(element.asText()));
            } catch (IllegalArgumentException e) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        field, "invalid", "Identificador inválido na carga do evento: " + field));
            }
        }
        return Set.copyOf(values);
    }

    private static Instant optionalInstant(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Instante inválido na carga do evento: " + field));
        }
        try {
            return Instant.parse(node.asText());
        } catch (RuntimeException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Instante inválido na carga do evento (ISO-8601): " + field));
        }
    }

    private static boolean optionalBoolean(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return false;
        }
        if (!node.isBoolean()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    field, "invalid", "Campo inválido na carga do evento (esperado booleano): " + field));
        }
        return node.asBoolean();
    }
}
