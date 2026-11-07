package br.com.ziipsan.platform.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de retorno do provedor de e-mail, já traduzido da notificação bruta pela
 * borda que o recebe (na referência da LC.2, o tópico de notificações do SES):
 * {@code providerEventId} é a identidade do evento no provedor e é a chave de
 * idempotência — o mesmo evento entregue duas vezes produz os efeitos UMA vez.
 * {@code reason} é o código que o provedor deu (ex.: motivo do bounce); pode ser
 * nulo quando o provedor não o informa, e nunca é inventado aqui.
 */
public record EmailDeliveryEvent(
        String providerEventId, UUID tenantId, String providerMessageId,
        EmailEventType type, Instant occurredAt, String reason) {

    public EmailDeliveryEvent {
        if (providerEventId == null || providerEventId.isBlank() || tenantId == null
                || providerMessageId == null || providerMessageId.isBlank()
                || type == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "evento de e-mail exige id do provedor, tenant, id da mensagem, tipo e instante");
        }
    }
}
