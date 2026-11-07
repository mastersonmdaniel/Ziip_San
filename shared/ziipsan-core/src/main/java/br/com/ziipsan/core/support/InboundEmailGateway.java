package br.com.ziipsan.core.support;

import java.util.Optional;
import java.util.UUID;

/**
 * P4.8: portas da entrada de resposta por e-mail. As implementações de
 * produção são a fiação de integração (camada p4): a leitura do chamado
 * sobre {@code support.ticket} no contexto RLS do tenant e a escrita da
 * mensagem pelo {@link TicketService#addRequesterMessage} — que já
 * reabre chamado {@code RESOLVED}, recusa {@code CLOSED} e marca dado
 * pessoal, sem esta tarefa duplicar nenhuma dessas regras.
 */
public interface InboundEmailGateway {

    /** Chamado pelo número citado, já no contexto do tenant (vazio = não existe aqui). */
    Optional<InboundEmailPolicy.TicketFacts> findTicket(String ticketNumber);

    /**
     * Acrescenta a resposta como mensagem PÚBLICA do solicitante no
     * chamado e devolve o id da mensagem criada. Só é chamado depois de a
     * {@link InboundEmailPolicy} aceitar.
     */
    UUID appendRequesterMessage(UUID ticketId, UUID requesterId, String body);

    /** Verdadeiro quando este {@code messageId} já entrou (idempotência do gateway de e-mail). */
    boolean alreadyIngested(String messageId);

    /** Marca o {@code messageId} como ingerido, na mesma decisão da escrita. */
    void markIngested(String messageId, UUID ticketId, UUID messageIdCreated);
}
