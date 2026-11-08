package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.RenderedMessage;

import java.util.UUID;

/**
 * Portas de envio dos canais externos da ED-41 — UMA por família de canal,
 * agrupadas neste arquivo como em {@code NegativationPorts} da ED-30.
 *
 * <p><b>Fronteira honesta (regra da cascata):</b> nenhum provedor, endpoint,
 * SDK ou credencial é nomeado aqui — o provedor de cada canal é contratado
 * por tenant, consta da lista de suboperadores do DPA da P3.9 e a
 * implementação concreta destas portas nasce na integração/LC com o
 * provedor real. O que esta entrega prova localmente é o NÚCLEO: guardas,
 * opt-in, classificação, custo, teto e medição, sobre fakes destas portas.
 * O envio devolve o id da mensagem no provedor — a chave que correlaciona
 * os eventos de entrega ({@link ChannelDeliveryEvent}).
 */
public final class ChannelGateways {

    private ChannelGateways() {
    }

    /** Envio de SMS a partir do remetente AUTENTICADO do tenant (MP 1.396/2026, aplicada pela ED-41). */
    public interface SmsGateway {
        String send(UUID tenantId, String senderId, String destination, RenderedMessage message);
    }

    /** Envio de WhatsApp a partir da conta do tenant, com a categoria de preço do modelo declarada ao provedor. */
    public interface WhatsAppGateway {
        String send(UUID tenantId, String accountId, String destination, RenderedMessage message,
                    WhatsAppCategory category);
    }

    /** Envio de push ao token do dispositivo do destinatário. */
    public interface PushGateway {
        String send(UUID tenantId, String deviceToken, RenderedMessage message);
    }
}
