package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Ingestão de uma mensagem da outbox na central (P3.36 sobre P1.9 + P1.38):
 *
 * <ol>
 *   <li>o evento PRECISA estar no catálogo da P1.38, na versão declarada — fora do catálogo
 *       ou em versão não declarada, a mensagem é recusada (409) e NENHUM aviso nasce;</li>
 *   <li>a carga é lida pela {@code Json} do core; campo obrigatório ausente é recusa (422)
 *       nomeando o campo — a central não inventa destinatário nem texto;</li>
 *   <li>avisos de revogação de consentimento/acesso assistido e de sessão de acesso
 *       assistido (ADR-0020) entram pela MESMA porta: são eventos do catálogo nas categorias
 *       {@code SECURITY}/{@code LEGAL_NOTICE}, portanto obrigatórios e imediatos;</li>
 *   <li>o rascunho CARREGA o id da mensagem ({@code sourceMessageId}): é a chave de
 *       deduplicação que o serviço persiste em {@code notifications.source_message_id} —
 *       descartá-lo tornaria a ingestão não idempotente (correção pós-revisão).</li>
 * </ol>
 *
 * <p><b>Convenção desta porta:</b> {@code eventVersion}, {@code titleCode} e
 * {@code bodyCode} DENTRO da carga são convenção DESTA porta até o catálogo da P1.38 ser
 * espelhado (ver {@link OutboxMessage}); não são contrato confirmado da P1.38.
 */
public final class NotificationIngestRule {

    private NotificationIngestRule() {
    }

    /** Aviso pronto para gravar em {@code notifications} (a decisão de canais é do serviço, com as preferências). */
    public record NotificationDraft(UUID sourceMessageId, UUID tenantId, UUID recipientUserId, UUID recipientCustomerId,
                                    String eventName, int eventVersion, NotificationCategory category,
                                    String titleCode, String bodyCode, String payloadJson) {
    }

    public static NotificationDraft draft(OutboxMessage message, BusinessEventCatalog catalog) {
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
        if (userId == null && customerId == null) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "recipientUserId", "required", "A carga do evento não informa o destinatário do aviso."));
        }
        return new NotificationDraft(message.id(), message.tenantId(), userId, customerId,
                event.name(), event.version(), event.category(),
                requiredText(payload, "titleCode"), requiredText(payload, "bodyCode"),
                message.payloadJson());
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
