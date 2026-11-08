package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.platform.notification.multichannel.WhatsAppCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma mensagem da conversa (ED-42), gravada na conversa e, por ela, no
 * protocolo da matrícula. {@code externalMessageId} é o id da mensagem
 * no provedor/canal (correlação e idempotência na borda); em mensagem do
 * chat do portal é o id da sessão/mensagem do portal.
 *
 * <p>Mensagem outbound de WhatsApp montada de MODELO carrega a categoria
 * de preço do modelo da ED-41 ({@link WhatsAppCategory}, lida de
 * {@code msg.template_category_map}) — é dela que sai o custo por
 * categoria de template do aceite (GES-19). Texto livre de atendente no
 * WhatsApp (janela de atendimento aberta pelo morador) não tem modelo:
 * {@code templateCode}/{@code whatsappCategory} nulos, custo medido por
 * contagem — nenhum custo é presumido.
 */
public record ConversationMessage(
        UUID id, UUID tenantId, UUID conversationId, Direction direction, Author author,
        String body, String externalMessageId, String templateCode, WhatsAppCategory whatsappCategory,
        Instant occurredAt) {

    public enum Direction { INBOUND, OUTBOUND }

    public enum Author { CUSTOMER, ATTENDANT, SYSTEM }

    public ConversationMessage {
        if (id == null || tenantId == null || conversationId == null || direction == null
                || author == null || occurredAt == null) {
            throw new IllegalArgumentException("mensagem exige id, tenant, conversa, direção, autor e instante");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("mensagem exige o texto (mensagem vazia não é mensagem)");
        }
        if (direction == Direction.INBOUND && author != Author.CUSTOMER) {
            throw new IllegalArgumentException("mensagem inbound é do morador");
        }
        if (direction == Direction.OUTBOUND && author == Author.CUSTOMER) {
            throw new IllegalArgumentException("mensagem outbound não é do morador");
        }
        if (templateCode != null && whatsappCategory == null) {
            throw new IllegalArgumentException("mensagem de modelo exige a categoria de preço do modelo (ED-41)");
        }
    }
}
