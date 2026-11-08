package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationChannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tentativa de entrega de um aviso por SMS, WhatsApp ou push (ED-41), com a
 * prova que o provedor devolver e o custo estimado no envio — é a linha que
 * alimenta a entregabilidade medida e o teto mensal. {@code noticeId} é a
 * identidade do aviso (o id da mensagem da outbox da P1.9), a mesma chave
 * das tentativas de e-mail da P3.5. {@code whatsappCategory} só existe para
 * o WhatsApp (a categoria de preço do modelo); {@code estimatedCost} só
 * existe nos canais precificados (SMS/WhatsApp) — push é medido por
 * contagem, sem custo por mensagem nesta entrega (decisão declarada no
 * relatório).
 */
public record ChannelDelivery(
        UUID id, UUID tenantId, UUID noticeId, UUID customerId, UUID userId, UUID contactId,
        NotificationChannel channel, String destination, String templateCode, int templateVersion,
        WhatsAppCategory whatsappCategory, ChannelDeliveryStatus status, String providerMessageId,
        BigDecimal estimatedCost, Instant sentAt, Instant resolvedAt) {

    public ChannelDelivery {
        if (id == null || tenantId == null || noticeId == null || channel == null
                || destination == null || destination.isBlank() || templateCode == null
                || templateCode.isBlank() || status == null || sentAt == null) {
            throw new IllegalArgumentException(
                    "tentativa multicanal exige id, tenant, aviso, canal, destino, modelo, situação e envio");
        }
        if (customerId == null && userId == null) {
            throw new IllegalArgumentException("tentativa multicanal exige o destinatário (usuário ou consumidor)");
        }
        if (channel == NotificationChannel.WHATSAPP && whatsappCategory == null) {
            throw new IllegalArgumentException("tentativa de WhatsApp exige a categoria de preço do modelo");
        }
        if (channel == NotificationChannel.SMS || channel == NotificationChannel.WHATSAPP) {
            if (estimatedCost == null || estimatedCost.signum() < 0) {
                throw new IllegalArgumentException("tentativa paga (SMS/WhatsApp) exige o custo estimado >= 0");
            }
        }
    }

    /** Cópia com a situação resolvida pelo evento do provedor (a tentativa em si é imutável). */
    public ChannelDelivery resolved(ChannelDeliveryStatus newStatus, Instant at) {
        if (newStatus == ChannelDeliveryStatus.SENT || at == null) {
            throw new IllegalArgumentException("a resolução exige o evento do provedor e o instante dele");
        }
        return new ChannelDelivery(id, tenantId, noticeId, customerId, userId, contactId, channel,
                destination, templateCode, templateVersion, whatsappCategory, newStatus,
                providerMessageId, estimatedCost, sentAt, at);
    }
}
