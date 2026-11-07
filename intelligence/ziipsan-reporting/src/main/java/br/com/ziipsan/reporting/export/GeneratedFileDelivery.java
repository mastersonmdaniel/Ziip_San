package br.com.ziipsan.reporting.export;

import java.util.UUID;

/**
 * Porta de entrega assincrona: o arquivo gerado pelo worker vira um item de
 * "Arquivos gerados" (tabela generated_files da P1.12, V3_11) - o MESMO
 * ponto de entrega das exportacoes de lista e dos relatorios agendados.
 */
public interface GeneratedFileDelivery {

    /** Cria o item QUEUED e devolve o id (o chamador preenche ao concluir). */
    UUID enqueue(UUID tenantId, UUID userId, String resource, String fileName);

    void complete(UUID generatedFileId, long rowCount, byte[] content);

    void fail(UUID generatedFileId, String error);
}
