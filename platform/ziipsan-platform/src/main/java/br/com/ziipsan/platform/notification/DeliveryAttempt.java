package br.com.ziipsan.platform.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Tentativa de entrega de um aviso por um canal externo (P3.5), com a prova que o
 * provedor devolveu. {@code noticeId} é a identidade do aviso: o id da mensagem da
 * outbox (P1.9) que o originou — a mesma chave que a P3.36 grava em
 * {@code notifications.source_message_id}; a regra de prova do corte (P3.4) consulta
 * por ele. {@code providerMessageId} é o id que o gateway de e-mail devolveu no envio
 * e é a chave de correlação dos eventos {@code delivered/bounced/complained}.
 *
 * <p>O endereço NÃO vem da carga do evento (a P1.38 proíbe dado pessoal na carga):
 * é o endereço do contato resolvido no cadastro da P1.3 no momento do envio.
 */
public record DeliveryAttempt(
        UUID id, UUID tenantId, UUID noticeId, UUID customerId, UUID userId, UUID contactId,
        NotificationChannel channel, String emailAddress, String templateCode, int templateVersion,
        DeliveryStatus status, String providerMessageId, Instant sentAt, Instant resolvedAt) {

    public DeliveryAttempt {
        if (id == null || tenantId == null || noticeId == null || channel == null
                || emailAddress == null || emailAddress.isBlank() || templateCode == null
                || templateCode.isBlank() || status == null || sentAt == null) {
            throw new IllegalArgumentException(
                    "tentativa de entrega exige id, tenant, aviso, canal, endereço, modelo, situação e envio");
        }
        if (customerId == null && userId == null) {
            throw new IllegalArgumentException("tentativa de entrega exige o destinatário (usuário ou consumidor)");
        }
    }

    /** Cópia com a situação resolvida pelo evento do provedor (a tentativa em si é imutável). */
    public DeliveryAttempt resolved(DeliveryStatus newStatus, Instant at) {
        if (newStatus == DeliveryStatus.SENT || at == null) {
            throw new IllegalArgumentException("a resolução exige o evento do provedor e o instante dele");
        }
        return new DeliveryAttempt(id, tenantId, noticeId, customerId, userId, contactId, channel,
                emailAddress, templateCode, templateVersion, newStatus, providerMessageId, sentAt, at);
    }
}
