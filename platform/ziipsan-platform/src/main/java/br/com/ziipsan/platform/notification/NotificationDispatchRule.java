package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Do evento da outbox ao plano de disparo externo (P3.5 sobre P1.9 + P1.38 + P2.30):
 *
 * <ol>
 *   <li>o evento PRECISA estar no catálogo da P1.38, na versão declarada — fora do
 *       catálogo, nenhum disparo nasce (409), a mesma recusa da ingestão da P3.36;</li>
 *   <li>a mensagem é montada pelo motor de modelos da P2.30: a carga declara o
 *       {@code templateCode} do modelo do tenant — esta tarefa não tem leiaute próprio;</li>
 *   <li>a carga declara a {@code purposeCode} (finalidade do catálogo da P1.3):
 *       é por ela, com a categoria do evento e o canal, que as preferências da
 *       P3.36 são lidas;</li>
 *   <li>as {@code variables} do modelo vêm da carga como textos (códigos e
 *       identificadores — a P1.38 proíbe dado pessoal na carga); endereço e nome
 *       do destinatário NUNCA vêm da carga.</li>
 * </ol>
 *
 * <p><b>Convenção desta porta</b> (mesma declaração da P3.36): {@code eventVersion},
 * {@code templateCode}, {@code purposeCode}, {@code recipientUserId}/
 * {@code recipientCustomerId} e {@code variables} DENTRO da carga são a convenção
 * desta tarefa até o catálogo da P1.38 ser espelhado; com o catálogo real, o
 * esquema dele manda e esta convenção é revisada. Campo obrigatório ausente é
 * recusa (422) nomeando o campo — nada é presumido.
 */
public final class NotificationDispatchRule {

    private NotificationDispatchRule() {
    }

    /** Plano de disparo de UM aviso externo: o que montar, para quem, sob qual finalidade. */
    public record DispatchDraft(UUID noticeId, UUID tenantId, UUID recipientUserId, UUID recipientCustomerId,
                                String eventName, int eventVersion, NotificationCategory category,
                                String purposeCode, String templateCode, Map<String, String> variables) {
    }

    public static DispatchDraft draft(OutboxMessage message, BusinessEventCatalog catalog) {
        if (message == null || catalog == null) {
            throw new IllegalArgumentException("mensagem e catálogo são obrigatórios");
        }
        JsonNode payload = Json.readTree(message.payloadJson());
        int version = requiredInt(payload, "eventVersion");
        CatalogEvent event = catalog.find(message.type(), version).orElseThrow(() ->
                new ApiException.Conflict("event-not-in-catalog",
                        "Evento fora do catálogo de eventos ou em versão não declarada: " + message.type()));
        UUID userId = optionalUuid(payload, "recipientUserId");
        UUID customerId = optionalUuid(payload, "recipientCustomerId");
        if ((userId == null) == (customerId == null)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "recipientUserId", "exactly-one-recipient",
                    "A carga do evento exige exatamente um destinatário: usuário OU consumidor."));
        }
        return new DispatchDraft(message.id(), message.tenantId(), userId, customerId,
                event.name(), event.version(), event.category(),
                requiredText(payload, "purposeCode"), requiredText(payload, "templateCode"),
                variables(payload));
    }

    private static Map<String, String> variables(JsonNode payload) {
        JsonNode node = payload.get("variables");
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "variables", "invalid", "As variáveis do modelo devem ser um objeto de textos."));
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (!value.isTextual() && !value.isNumber()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "variables." + entry.getKey(), "invalid",
                        "Variável do modelo deve ser texto ou número: " + entry.getKey()));
            }
            result.put(entry.getKey(), value.asText());
        }
        return Map.copyOf(result);
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
}
