package br.com.ziipsan.platform.notification.multichannel;

import java.util.UUID;

/**
 * Contato de um canal multicanal resolvido no cadastro da P1.3:
 * {@code destination} é o telefone (SMS/WhatsApp) ou o token do dispositivo
 * (push) NO MOMENTO do envio. {@code verified} é o estado de verificação do
 * contato no cadastro: contato não verificado não recebe disparo (mesma
 * guarda do e-mail na P3.5).
 */
public record ChannelContact(UUID contactId, UUID customerId, UUID userId, String destination, boolean verified) {

    public ChannelContact {
        if (contactId == null || destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("contato de canal exige id, destino e estado de verificação");
        }
        if (customerId == null && userId == null) {
            throw new IllegalArgumentException("contato de canal exige o titular (usuário ou consumidor)");
        }
    }
}
