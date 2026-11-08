package br.com.ziipsan.commercial.fiscal.queue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Porta de leitura dos documentos fiscais do tenant para a fila (P2.33), sobre o
 * dicionário da V4_5 + extensão da V4_7 (P2.6) e as pendências da V4_11. A
 * implementação JPA/JDBC é da integração (o dicionário {@code fiscal_documents} não
 * foi espelhado nesta tarefa — ver relatório); esta porta é o contrato que a fila
 * consome, no mesmo desenho da {@code NfageDocumentoStore} da P2.6.
 */
public interface FiscalDocumentReadPort {

    /** Todos os documentos do tenant candidatos à fila (a classificação filtra). */
    List<FiscalDocumentView> listByTenant(UUID tenantId);

    Optional<FiscalDocumentView> findById(UUID tenantId, UUID documentId);

    /**
     * Números JÁ USADOS de uma série de um emitente: todo número que consta em
     * documento da série (qualquer estado do dicionário, inclusive VOIDED — número
     * inutilizado não volta a ficar "não usado") ou reservado em
     * {@code fiscal_series}. É sobre este conjunto que a {@link VoidingRule} decide.
     */
    Set<Long> usedNumbers(UUID tenantId, UUID issuerId, int serie);
}
