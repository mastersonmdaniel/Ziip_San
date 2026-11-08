package br.com.ziipsan.platform.notification.multichannel;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de retorno do provedor de um canal multicanal, já traduzido da
 * notificação bruta pela borda que o recebe: {@code providerEventId} é a
 * identidade do evento no provedor e a chave de idempotência (o mesmo
 * evento entregue duas vezes produz os efeitos UMA vez, como nos eventos
 * de e-mail da P3.5). {@code reason} é o código que o provedor deu; pode
 * ser nulo quando ele não o informa, e nunca é inventado aqui.
 */
public record ChannelDeliveryEvent(
        String providerEventId, UUID tenantId, String providerMessageId,
        ChannelEventType type, Instant occurredAt, String reason) {

    public enum ChannelEventType {
        DELIVERED, FAILED, REJECTED
    }

    public ChannelDeliveryEvent {
        if (providerEventId == null || providerEventId.isBlank() || tenantId == null
                || providerMessageId == null || providerMessageId.isBlank()
                || type == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "evento de canal exige id do provedor, tenant, id da mensagem, tipo e instante");
        }
    }
}
