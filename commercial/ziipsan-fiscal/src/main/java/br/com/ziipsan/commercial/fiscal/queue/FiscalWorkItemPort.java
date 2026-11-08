package br.com.ziipsan.commercial.fiscal.queue;

import java.util.UUID;

/**
 * Porta de abertura/encerramento do item de "minhas tarefas" da pendência fiscal,
 * endereçado ao RESPONSÁVEL PELO CONTRATO (aceite da P2.33, JOR-05 via P2.28). A
 * implementação é a ponte da plataforma ({@code FiscalDocumentWorkItems} +
 * {@code WorkInboxService.openDirect} da P2.28, nesta entrega); quem resolve QUAL
 * usuário é o responsável pelo contrato do tenant é a integração, a partir do
 * cadastro do contrato — esta tarefa não inventa esse vínculo: sem responsável
 * resolvido, a abertura é recusada pela fábrica da ponte, nunca endereçada "a
 * ninguém".
 */
public interface FiscalWorkItemPort {

    /** Abre (ou devolve o já aberto) o item da pendência; devolve o id do item. */
    UUID openPendency(UUID tenantId, FiscalPendencyKind kind, UUID fiscalDocumentId,
                      UUID contractResponsibleId, UUID requesterId);

    /** Encerra o item aberto da pendência do documento, se existir. */
    void closePendency(UUID tenantId, FiscalPendencyKind kind, UUID fiscalDocumentId, String closeReason);
}
